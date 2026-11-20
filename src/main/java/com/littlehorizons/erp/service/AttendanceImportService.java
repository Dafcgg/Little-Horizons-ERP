package com.littlehorizons.erp.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.littlehorizons.erp.dao.AttendanceImportDao;
import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.AttendanceImportResult;
import com.littlehorizons.erp.model.AttendanceStatus;
import com.littlehorizons.erp.util.AppLogger;
import com.littlehorizons.erp.util.BusinessException;
import com.littlehorizons.erp.util.ConnectionProvider;
import com.littlehorizons.erp.util.SchoolInformation;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class AttendanceImportService extends TransactionalService {
  private static final String INPUT_NAME = "asistencias_importar.csv";
  private static final String HEADER = "id_estudiante;id_materia;fecha;estado";
  private static final String MISSING_FILE =
      "Error: El archivo 'asistencias_importar.csv' no se encuentra en la carpeta raíz del proyecto.";
  private static final String INVALID_HEADER =
      "Error: El archivo no tiene el formato correcto. La cabecera debe ser: " + HEADER;
  private static final Pattern DATE_SHAPE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter REPORT_TIME =
      DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss", Locale.ROOT);
  private static final List<AcademicPeriod> QUARTERS =
      List.of(AcademicPeriod.I, AcademicPeriod.II, AcademicPeriod.III, AcademicPeriod.IV);

  private final AttendanceImportDao dao;
  private final Clock clock;

  public AttendanceImportService(ConnectionProvider connections, AttendanceImportDao dao) {
    this(connections, dao, Clock.system(ZoneId.of("America/Bogota")));
  }

  AttendanceImportService(ConnectionProvider connections, AttendanceImportDao dao, Clock clock) {
    super(connections);
    this.dao = dao;
    this.clock = clock;
  }

  public AttendanceImportResult importFrom(Path projectDirectory) throws IOException {
    try (BufferedReader input = openInput(projectDirectory.resolve(INPUT_NAME))) {
      String header = input.readLine();
      if (header != null && header.startsWith("\uFEFF")) header = header.substring(1);
      if (!HEADER.equals(header)) throw new BusinessException(INVALID_HEADER);

      Path directory = Files.createDirectories(projectDirectory.resolve("reports"));
      // Spool the details so memory usage does not grow with the number of rows.
      Path details = Files.createTempFile(directory, "attendance-import-", ".tmp");
      try {
        var byStatus = new EnumMap<AttendanceStatus, Long>(AttendanceStatus.class);
        long total = processRows(input, details, byStatus);
        long successful = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long failed = total - successful;
        Path report = reserveReport(directory);
        try {
          writeReport(report, details, total, successful, failed, byStatus);
        } catch (IOException failure) {
          try {
            Files.deleteIfExists(report);
          } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
          }
          throw failure;
        }
        return new AttendanceImportResult(total, successful, failed, report);
      } finally {
        Files.deleteIfExists(details);
      }
    }
  }

  private long processRows(BufferedReader input, Path details, Map<AttendanceStatus, Long> byStatus)
      throws IOException {
    try (BufferedWriter output = Files.newBufferedWriter(details, UTF_8)) {
      long lineNumber = 1;
      long total = 0;
      String line;
      while ((line = input.readLine()) != null) {
        lineNumber++;
        if (line.trim().isEmpty()) continue;
        total++;
        try {
          ImportRow row = parse(line);
          long attendanceId = insert(row);
          byStatus.merge(row.status(), 1L, Long::sum);
          output.write("S%d | %d | %d | %d | %s | %s%n".formatted(
              lineNumber, attendanceId, row.studentId(), row.subjectId(), row.date(),
              row.status().databaseValue()));
        } catch (BusinessException rejection) {
          String reason = rejection.getMessage();
          AppLogger.warning("Attendance CSV line %d rejected: %s".formatted(lineNumber, reason), null);
          output.write("E%d | %s | %s%n".formatted(lineNumber, line, reason));
        }
      }
      return total;
    }
  }

  private static void writeReport(
      Path report, Path details, long total, long successful, long failed,
      Map<AttendanceStatus, Long> byStatus) throws IOException {
    double effectiveness = total == 0 ? 0.0 : 100.0 * successful / total;
    try (BufferedWriter output = Files.newBufferedWriter(report, UTF_8)) {
      output.write(String.format(Locale.ROOT, """
          %s
          REPORTE DETALLADO DE IMPORTACIÓN DE ASISTENCIAS
          Archivo: %s

          RESUMEN ESTADÍSTICO
          Total de líneas en el archivo: %d
          Líneas procesadas exitosamente: %d
          Líneas con errores (no procesadas): %d
          Efectividad: %.2f%%

          DESGLOSE POR ESTADO
          Estado | Cantidad
          """, SchoolInformation.fromEnvironment().name(), INPUT_NAME, total, successful, failed,
          effectiveness));
      for (AttendanceStatus status : AttendanceStatus.values()) {
        output.write("%s | %d%n".formatted(status.databaseValue(), byStatus.getOrDefault(status, 0L)));
      }

      output.write("\nDETALLE DE ERRORES\nLínea del CSV | Contenido crudo | Motivo\n");
      if (failed == 0) output.write("Sin errores.\n");
      else appendDetails(output, details, 'E');

      output.write("\nREGISTROS IMPORTADOS\n"
          + "Línea del CSV | ID asistencia | ID estudiante | ID materia | Fecha | Estado\n");
      if (successful == 0) output.write("Sin registros importados.\n");
      else appendDetails(output, details, 'S');
    }
  }

  private static void appendDetails(BufferedWriter output, Path details, char marker)
      throws IOException {
    try (BufferedReader entries = Files.newBufferedReader(details, UTF_8)) {
      String entry;
      while ((entry = entries.readLine()) != null) {
        if (entry.charAt(0) == marker) {
          output.write(entry, 1, entry.length() - 1);
          output.newLine();
        }
      }
    }
  }

  private static BufferedReader openInput(Path path) throws IOException {
    try {
      return Files.newBufferedReader(path, UTF_8);
    } catch (NoSuchFileException missing) {
      throw new BusinessException(MISSING_FILE);
    }
  }

  private Path reserveReport(Path directory) throws IOException {
    LocalDateTime time = LocalDateTime.now(clock);
    while (true) {
      Path path = directory.resolve(
          "reporte_importacion_asistencias_" + REPORT_TIME.format(time) + ".txt");
      try {
        return Files.createFile(path);
      } catch (FileAlreadyExistsException collision) {
        time = time.plusSeconds(1);
      }
    }
  }

  private static ImportRow parse(String line) {
    String[] fields = line.split(";", -1);
    require(fields.length == 4, "Se esperaban exactamente 4 campos separados por punto y coma.");
    int studentId = integer(fields[0], "id_estudiante");
    int subjectId = integer(fields[1], "id_materia");
    LocalDate date = parseDate(fields[2]);
    AttendanceStatus status = Arrays.stream(AttendanceStatus.values())
        .filter(candidate -> candidate.databaseValue().equals(fields[3]))
        .findFirst()
        .orElseThrow(() -> new BusinessException("Estado de asistencia inválido."));
    return new ImportRow(studentId, subjectId, date, status);
  }

  private static int integer(String value, String column) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException invalid) {
      throw new BusinessException(column + " debe ser un número entero válido.");
    }
  }

  private static LocalDate parseDate(String value) {
    String error = "La fecha debe ser real y tener el formato AAAA-MM-DD.";
    require(DATE_SHAPE.matcher(value).matches(), error);
    try {
      return LocalDate.parse(value, DATE_FORMAT);
    } catch (DateTimeParseException invalid) {
      throw new BusinessException(error);
    }
  }

  private long insert(ImportRow row) {
    // The CSV has no period and the schema requires one. Use its calendar quarter.
    AcademicPeriod period = QUARTERS.get((row.date().getMonthValue() - 1) / 3);
    return transaction(
        connection -> {
          require(dao.studentExists(connection, row.studentId()), "El estudiante no existe.");
          require(dao.subjectExists(connection, row.subjectId()), "La materia no existe.");
          return dao.insert(connection, row.studentId(), row.subjectId(), row.date(), row.status(), period);
        });
  }

  private record ImportRow(int studentId, int subjectId, LocalDate date, AttendanceStatus status) {}
}
