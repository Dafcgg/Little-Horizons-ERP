package com.littlehorizons.erp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.util.ConnectionProvider;
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
  void missingCsvReturnsDirectlyToTheMainMenu() {
    String output = runImport(projectDirectory, noDatabase(), "7\n0\n");

    assertTrue(output.contains("7. TEST - Importar Asistencias desde CSV"));
    assertTrue(output.lines().anyMatch(
        "Error: El archivo 'asistencias_importar.csv' no se encuentra en la carpeta raíz del proyecto."
            ::equals));
    assertTrue(output.endsWith("Hasta pronto." + System.lineSeparator()));
    assertFalse(Files.exists(projectDirectory.resolve("reports")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "ID_estudiante;id_materia;fecha;estado\n1;1;2024-01-01;Presente\n",
      "id_estudiante;id_materia;fecha;estado \n", "\uFEFF\uFEFFid_estudiante;id_materia;fecha;estado\n"})
  void invalidHeaderStopsBeforeProcessingAnyData(String contents) throws Exception {
    Files.writeString(projectDirectory.resolve("asistencias_importar.csv"), contents, UTF_8);

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
    Files.writeString(projectDirectory.resolve("asistencias_importar.csv"),
        prefix + "id_estudiante;id_materia;fecha;estado\n\n \t \n", UTF_8);

    String output = runImport(projectDirectory, noDatabase(), "7\nno es Enter vacío\n\n0\n");

    assertTrue(output.contains("Total de líneas en el archivo: 0"));
    assertTrue(output.contains("Líneas procesadas exitosamente: 0"));
    assertTrue(output.contains("Líneas con errores (no procesadas): 0"));
    assertTrue(output.contains("Presione Enter para volver al menú principal..."));
    assertFalse(output.contains("Dato inválido"));
    assertTrue(output.endsWith("Hasta pronto." + System.lineSeparator()));
  }

  static String runImport(Path directory, ConnectionProvider connections, String input) {
    var output = new ByteArrayOutputStream();
    var view = new ConsoleView(new ByteArrayInputStream(input.getBytes(UTF_8)),
        new PrintStream(output, true, UTF_8));
    Main.run(view, connections, invoice -> {
      throw new AssertionError("Attendance import must not render an invoice");
    }, directory);
    return output.toString(UTF_8);
  }

  private static ConnectionProvider noDatabase() {
    return () -> { throw new AssertionError("This input must not open a database connection"); };
  }
}
