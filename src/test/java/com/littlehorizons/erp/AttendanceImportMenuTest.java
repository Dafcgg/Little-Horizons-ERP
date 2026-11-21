package com.littlehorizons.erp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.util.AppLogger;
import com.littlehorizons.erp.util.ConnectionProvider;
import com.littlehorizons.erp.util.ReportDownload;
import com.littlehorizons.erp.view.ConsoleView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttendanceImportMenuTest {
  @TempDir Path projectDirectory;

  @Test
  void missingTxtReturnsDirectlyToTheMainMenu() {
    String output = runImport(projectDirectory, noDatabase(), "7\n0\n");

    assertTrue(output.contains("7. TEST - Importar Asistencias desde TXT"));
    assertTrue(output.lines().anyMatch(
        "Error: El archivo 'asistencias_importar.txt' no se encuentra en la carpeta raíz del proyecto."
            ::equals));
    assertTrue(output.endsWith("Hasta pronto." + System.lineSeparator()));
    assertFalse(Files.exists(projectDirectory.resolve("reports")));
    assertFalse(Files.exists(projectDirectory.resolve("downloads")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "ID_estudiante;id_materia;fecha;estado\n1;1;2024-01-01;Presente\n",
      "id_estudiante;id_materia;fecha;estado \n", "\uFEFF\uFEFFid_estudiante;id_materia;fecha;estado\n"})
  void invalidHeaderStopsBeforeProcessingAnyData(String contents) throws Exception {
    Files.writeString(projectDirectory.resolve("asistencias_importar.txt"), contents, UTF_8);

    String output = runImport(projectDirectory, noDatabase(), "7\n0\n");

    String message = "Error: El archivo no tiene el formato correcto. La cabecera debe ser: "
        + "id_estudiante;id_materia;fecha;estado";
    assertTrue(output.lines().anyMatch(message::equals));
    assertFalse(output.contains("Presione Enter"));
    assertFalse(Files.exists(projectDirectory.resolve("reports")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\uFEFF"})
  void headerAndBlankRowsProduceAnEmptyReportAndWaitForEnter(String prefix) throws Exception {
    Files.writeString(projectDirectory.resolve("asistencias_importar.txt"),
        prefix + "id_estudiante;id_materia;fecha;estado\n\n \t \n\u2003\u00A0\n", UTF_8);

    String output = runImport(projectDirectory, noDatabase(), "7\nno es Enter vacío\n\n0\n");

    assertTrue(output.contains("Total de líneas en el archivo: 0"));
    assertTrue(output.contains("Líneas procesadas exitosamente: 0"));
    assertTrue(output.contains("Líneas con errores (no procesadas): 0"));
    assertTrue(output.contains("Presione Enter para volver al menú principal..."));
    assertFalse(output.contains("Dato inválido"));
    assertTrue(output.endsWith("Hasta pronto." + System.lineSeparator()));
    try (var reports = Files.list(projectDirectory.resolve("reports"))) {
      Path report = reports.findFirst().orElseThrow();
      assertTrue(Files.readString(report, UTF_8).lines().anyMatch(
          line -> line.matches("│ Líneas vacías omitidas +│ 3 +│")));
      Path downloaded = projectDirectory.resolve("downloads").resolve(report.getFileName());
      assertEquals(-1, Files.mismatch(report, downloaded));
      assertTrue(output.contains("📄 Reporte guardado en el proyecto : reports/" + report.getFileName()));
      assertTrue(output.contains("📥 Reporte descargado en el equipo  : " + downloaded.toAbsolutePath()));
    }
  }

  @Test
  void unavailableDownloadDirectoryKeepsTheProjectReportAndReturnsToMenu() throws Exception {
    AppLogger.configure();
    Files.writeString(projectDirectory.resolve("asistencias_importar.txt"),
        "id_estudiante;id_materia;fecha;estado\n", UTF_8);
    Path unavailable = Files.writeString(projectDirectory.resolve("downloads"), "Existing file", UTF_8);

    String output = runImport(projectDirectory, noDatabase(), "7\n\n0\n");

    assertTrue(output.contains("📄 Reporte guardado en el proyecto : reports/"));
    assertTrue(output.contains("No se pudo guardar la copia en Descargas"));
    assertFalse(output.contains("📥 Reporte descargado en el equipo"));
    assertTrue(output.endsWith("Hasta pronto." + System.lineSeparator()));
    assertEquals("Existing file", Files.readString(unavailable, UTF_8));
    try (var reports = Files.list(projectDirectory.resolve("reports"))) {
      assertTrue(Files.readString(reports.findFirst().orElseThrow(), UTF_8)
          .contains("REPORTE DE IMPORTACIÓN DE ASISTENCIAS"));
    }
    Path log = Path.of(System.getProperty("erp.log.path", "error_log.txt"));
    assertTrue(Files.readString(log, UTF_8)
        .contains("[WARNING] Could not copy attendance report to Downloads"));
  }

  static String runImport(Path directory, ConnectionProvider connections, String input) {
    var output = new ByteArrayOutputStream();
    var view = new ConsoleView(new ByteArrayInputStream(input.getBytes(UTF_8)),
        new PrintStream(output, true, UTF_8));
    Main.run(view, connections, invoice -> {
      throw new AssertionError("Attendance import must not render an invoice");
    }, directory, new ReportDownload(directory.resolve("downloads")));
    return output.toString(UTF_8);
  }

  private static ConnectionProvider noDatabase() {
    return () -> { throw new AssertionError("This input must not open a database connection"); };
  }
}
