package com.littlehorizons.erp.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.littlehorizons.erp.dao.AttendanceImportDao;
import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.AttendanceImportResult;
import com.littlehorizons.erp.model.AttendanceStatus;
import com.littlehorizons.erp.util.AppLogger;
import com.littlehorizons.erp.util.BusinessException;
import com.littlehorizons.erp.util.ConnectionProvider;
import com.littlehorizons.erp.util.AttendanceReportWriter;
import com.littlehorizons.erp.model.AttendanceNames;
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
  private static final String INPUT_NAME = "asistencias_importar.txt";
  private static final String HEADER = "id_estudiante;id_materia;fecha;estado";
  private static final String MISSING_FILE =
      "Error: El archivo 'asistencias_importar.txt' no se encuentra en la carpeta raíz del proyecto.";
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
    LocalDateTime started = LocalDateTime.now(clock);
    try (BufferedReader input = openInput(projectDirectory.resolve(INPUT_NAME))) {
      String header = input.readLine();
      if (header != null && header.startsWith("\uFEFF")) header = header.substring(1);
      if (!HEADER.equals(header)) throw new BusinessException(INVALID_HEADER);

      Path directory = Files.createDirectories(projectDirectory.resolve("reports"));
      // Spool the details so memory usage does not grow with the number of rows.
      Path details = Files.createTempFile(directory, "attendance-import-", ".tmp");
      try {
        var byStatus = new EnumMap<AttendanceStatus, Long>(AttendanceStatus.class);
        Counts counts = processRows(input, details, byStatus);
        long successful = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long failed = counts.total() - successful;
        Path report = reserveReport(directory, started);
        var result = new AttendanceImportResult(counts.total(), successful, failed, counts.skipped(), byStatus, report);
        try {
          AttendanceReportWriter.write(result, details, started);
        } catch (IOException failure) {
          try {
            Files.deleteIfExists(report);
          } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
          }
          throw failure;
        }
        return result;
      } finally {
        Files.deleteIfExists(details);
      }
    }
  }

  private Counts processRows(BufferedReader input, Path details, Map<AttendanceStatus, Long> byStatus)
      throws IOException {
    try (BufferedWriter output = Files.newBufferedWriter(details, UTF_8)) {
      long lineNumber = 1;
      long total = 0;
      long skipped = 0;
      String line;
      while ((line = input.readLine()) != null) {
        lineNumber++;
        if (line.codePoints().allMatch(
            point -> Character.isWhitespace(point) || Character.isSpaceChar(point))) {
          skipped++;
          continue;
        }
        total++;
        String[] fields = line.split(";", -1);
        try {
          ImportRow row = parse(fields);
          SavedRow saved = insert(row);
          byStatus.merge(row.status(), 1L, Long::sum);
          AttendanceReportWriter.saved(output, lineNumber, saved.id(),
              row.studentId() + " - " + saved.names().student(),
              row.subjectId() + " - " + saved.names().subject(), row.date().toString(),
              row.status().databaseValue());
        } catch (BusinessException rejection) {
          String reason = rejection.getMessage();
          AppLogger.warning("Attendance TXT line %d rejected: %s".formatted(lineNumber, reason), null);
          AttendanceReportWriter.rejected(output, lineNumber, line, reason, rejectedNames(fields));
        }
      }
      return new Counts(total, skipped);
    }
  }

  private AttendanceNames rejectedNames(String[] fields) {
    Integer student = optionalId(fields[0]);
    Integer subject = fields.length > 1 ? optionalId(fields[1]) : null;
    if (student != null || subject != null) {
      try {
        return transaction(connection -> dao.names(connection, student, subject));
      } catch (BusinessException unavailable) {
        AppLogger.warning("Could not resolve attendance names for the report", unavailable);
      }
    }
    return new AttendanceNames(null, null);
  }

  private static Integer optionalId(String value) {
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException invalid) {
      return null;
    }
  }

  private static BufferedReader openInput(Path path) throws IOException {
    try {
      return Files.newBufferedReader(path, UTF_8);
    } catch (NoSuchFileException missing) {
      throw new BusinessException(MISSING_FILE);
    }
  }

  private Path reserveReport(Path directory, LocalDateTime time) throws IOException {
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

  private static ImportRow parse(String[] fields) {
    require(fields.length == 4, "Se esperaban exactamente 4 campos separados por punto y coma.");
    int studentId = integer(fields[0], "id_estudiante");
    int subjectId = integer(fields[1], "id_materia");
    LocalDate date = parseDate(fields[2]);
    AttendanceStatus status = Arrays.stream(AttendanceStatus.values())
        .filter(candidate -> candidate.databaseValue().equals(fields[3]))
        .findFirst()
        .orElseThrow(() -> new BusinessException("Estado de asistencia inválido. Use Presente, Ausente, Tardanza o Excusa Médica."));
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

  private SavedRow insert(ImportRow row) {
    // The input file has no period and the schema requires one. Use its calendar quarter.
    AcademicPeriod period = QUARTERS.get((row.date().getMonthValue() - 1) / 3);
    return transaction(
        connection -> {
          AttendanceNames names = dao.names(connection, row.studentId(), row.subjectId());
          require(names.student() != null, "El estudiante no existe. Verifique su ID en el listado de estudiantes.");
          require(names.subject() != null, "La materia no existe. Verifique su ID en el listado de materias.");
          long id = dao.insert(connection, row.studentId(), row.subjectId(), row.date(), row.status(), period);
          return new SavedRow(id, names);
        });
  }

  private record Counts(long total, long skipped) {}

  private record SavedRow(long id, AttendanceNames names) {}

  private record ImportRow(int studentId, int subjectId, LocalDate date, AttendanceStatus status) {}
}
