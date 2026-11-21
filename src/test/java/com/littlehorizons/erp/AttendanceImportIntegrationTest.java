package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.CONNECTIONS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.Jdbc;
import com.littlehorizons.erp.util.AppLogger;
import com.littlehorizons.erp.util.SchoolInformation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class AttendanceImportIntegrationTest {
  @TempDir Path projectDirectory;

  @Test
  void validRowsCommitBeforeAndAfterInvalidRowsAndDuplicateInsert() throws Exception {
    AppLogger.configure();
    long studentId = createStudent();
    try {
      writeAttendanceFile(studentId);
      String output = AttendanceImportMenuTest.runImport(projectDirectory, CONNECTIONS, "7\n\n0\n");
      var name = Pattern.compile("reports/(reporte_importacion_asistencias_[0-9]{8}_[0-9]{6}\\.txt)")
          .matcher(output);
      assertTrue(name.find(), output);
      for (String expected : List.of("REPORTE DE IMPORTACIÓN DE ASISTENCIAS",
          "Total de líneas en el archivo: 20", "Líneas procesadas exitosamente: 4",
          "Líneas con errores (no procesadas): 16", "⚠ Carga con rechazos (20.00% de efectividad)",
          "Presione Enter para volver al menú principal...", "DESGLOSE RÁPIDO POR ESTADO")) {
        assertTrue(output.contains(expected), output);
      }
      assertFalse(output.contains("Ya existe una asistencia"));
      String report = Files.readString(projectDirectory.resolve("reports").resolve(name.group(1)), UTF_8);
      String subject;
      try (var connection = CONNECTIONS.open()) {
        subject = Jdbc.query(connection, "SELECT nombre_materia FROM materias WHERE id_materia=?",
            row -> row.getString(1), 1).get(0);
      }
      assertTrue(report.contains(SchoolInformation.fromEnvironment().name().toUpperCase(java.util.Locale.ROOT)));
      assertTrue(report.contains("20.00%"));
      assertTrue(report.matches("(?s).*Líneas vacías omitidas +│ 2 +│.*"));
      assertTrue(report.contains("█████░░░░░░░░░░░░░░░"));
      assertEquals(4, report.lines().filter(line -> line.contains("25.00%")).count());
      String audit = report.split("AUDITORÍA DE REGISTROS RECHAZADOS")[1]
          .split("DETALLE COMPLETO DE ASISTENCIAS REGISTRADAS")[0];
      String unknownStudent = audit.substring(audit.indexOf("2147483647;1;2044-04-08"),
          audit.indexOf(";2147483647;2044-04-09"));
      assertTrue(unknownStudent.contains("Materia: " + subject));
      String unknownSubject = audit.substring(audit.indexOf(";2147483647;2044-04-09"),
          audit.indexOf(";1;2044-04-10"));
      assertTrue(unknownSubject.contains("Estudiante: Prueba María Importación López"));
      assertTrue(audit.contains("Ya existe una asistencia"));
      assertTrue(audit.contains("2044-04-11;AUSENTE"));
      assertFalse(audit.lines().anyMatch(line -> line.matches("│ [34] +│.*")));
      try (var reports = Files.list(projectDirectory.resolve("reports"))) {
        assertEquals(1, reports.count());
      }

      try (var connection = CONNECTIONS.open()) {
        var rows = Jdbc.query(connection, """
            SELECT id_asistencia, fecha_asistencia, estado_asistencia, anio_academico,
                   periodo_academico, registrado_por, observaciones
            FROM asistencias WHERE id_estudiante=? AND id_materia=? AND anio_academico=?
            ORDER BY fecha_asistencia
            """, row -> {
              assertEquals(2044, row.getInt("anio_academico"));
              assertNull(row.getObject("registrado_por"));
              assertNull(row.getString("observaciones"));
              String status = row.getString("estado_asistencia");
              List<String> cells = List.of(Long.toString(row.getLong("id_asistencia")),
                  studentId + " - Prueba María Importación López", "1 - " + subject,
                  row.getString("fecha_asistencia"), status);
              assertTrue(report.lines().anyMatch(line -> {
                var parts = java.util.Arrays.stream(line.split("│")).map(String::strip).toList();
                return parts.size() == 7 && parts.subList(2, 7).equals(cells);
              }), report);
              return row.getString("periodo_academico") + ":" + status;
            }, studentId, 1, 2044);
        assertEquals(List.of("I:Presente", "II:Ausente", "III:Tardanza", "IV:Excusa Médica"), rows);
      }
      Path log = Path.of(System.getProperty("erp.log.path", "error_log.txt"));
      String logText = Files.readString(log, UTF_8);
      assertTrue(logText.contains("Duplicate entry '%d-1-2044-02-29'".formatted(studentId)));
      assertTrue(logText.contains("Attendance TXT line 21 rejected: Estado de asistencia inválido."));
    } finally {
      try (var connection = CONNECTIONS.open()) {
        Jdbc.update(connection, "DELETE FROM asistencias WHERE id_estudiante=?", studentId);
        Jdbc.update(connection, "DELETE FROM estudiantes WHERE id_estudiante=?", studentId);
      }
    }
  }

  private long createStudent() throws Exception {
    try (var connection = CONNECTIONS.open()) {
      return Jdbc.insert(connection, """
          INSERT INTO estudiantes (numero_documento, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,
            fecha_nacimiento, genero, fecha_ingreso)
          VALUES (?, 'Prueba', 'María', 'Importación', 'López', '2014-02-01', 'Otro', '2026-01-15')
          """, MysqlFixture.unique());
    }
  }

  private void writeAttendanceFile(long studentId) throws Exception {
    String row = studentId + ";1;";
    Files.writeString(projectDirectory.resolve("asistencias_importar.txt"), String.join("\r\n",
        "\uFEFFid_estudiante;id_materia;fecha;estado",
        row + "2044-02-29;Presente",
        "",
        "  \t ",
        row + "2044-02-29;Ausente",
        row + "2044-05-01;Ausente",
        row + "2044-08-01;Tardanza",
        row + "2044-11-01;Excusa Médica",
        row + "2044-02-30;Presente",
        row + "2044-2-01;Presente",
        row + "2044-04-01;presente",
        row + "2044-04-02;Excusa Medica",
        row + "2044-04-03;Presente ",
        row + "2044-04-04;Presente;",
        row + "2044-04-05",
        "texto;1;2044-04-06;Presente",
        studentId + ";2147483648;2044-04-07;Ausente",
        "2147483647;1;2044-04-08;Ausente",
        studentId + ";2147483647;2044-04-09;Ausente",
        row + "2044-04-10;",
        row + "2044-04-11;AUSENTE",
        row + "2044-04-12;Tarde",
        row + "2044-04-13;Justificado"), UTF_8);
  }
}
