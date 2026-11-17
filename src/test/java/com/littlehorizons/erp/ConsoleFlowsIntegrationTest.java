package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.CONNECTIONS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.view.ConsoleView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class ConsoleFlowsIntegrationTest {
  private static final String RETRY = "Dato inválido. Intente de nuevo.";
  private static final String CANCELLED = "Operación cancelada.";
  private static final String PROMPT = " (q: cancelar): ";
  private static final String INVALID_OPTIONS = "texto\n🔥;\n-1\n99\n2147483648\n\n";
  private static final int INVALID_CYCLES = 60;
  private static final String[] PAYMENT_INPUTS = {
    "1", "1.00", "2024-02-29", "1", "NEW-RECEIPT", "Reason"
  };

  static Stream<String> menus() {
    return Stream.of(
        "", "1", "2", "3", "4", "5", "6", "6 1", "6 2", "6 3", "6 4", "6 5", "6 6", "6 6 1",
        "6 6 2");
  }

  @ParameterizedTest(name = "menu [{0}]")
  @MethodSource("menus")
  void rejectsThreeHundredSixtyInvalidOptionsPerMenu(String path) {
    String output = session(lines(navigate(path)) + INVALID_OPTIONS.repeat(INVALID_CYCLES));
    assertEquals(6 * INVALID_CYCLES, occurrences(output, RETRY));
    assertClosedCleanly(output);
  }

  record Form(String path, List<String> inputs) {
    Form(String path, String... inputs) {
      this(path, List.of(inputs));
    }
  }

  record InterruptedForm(Form form, int fieldsCompleted, boolean cancel) {
    @Override
    public String toString() {
      return form.path + ", field " + (fieldsCompleted + 1) + ", " + (cancel ? "q" : "EOF");
    }
  }

  static Stream<InterruptedForm> formInterruptions() {
    return forms()
        .flatMap(
            form ->
                IntStream.range(0, form.inputs.size())
                    .boxed()
                    .flatMap(
                        fields ->
                            Stream.of(
                                new InterruptedForm(form, fields, true),
                                new InterruptedForm(form, fields, false))));
  }

  // Each path ends at an action with interactive fields, or at the catalog selector.
  private static Stream<Form> forms() {
    return Stream.concat(
        Stream.of(
            new Form("1 3", "G-1", "Gina", "Test", "555", "Address"),
            new Form("1 4", "S-1", "Student", "Test", "2012-02-29", "Address", "1", "Tutor"),
            new Form(
                "1 5",
                "S-2",
                "Student",
                "Test",
                "2012-02-29",
                "Address",
                "G-2",
                "Gina",
                "Test",
                "555",
                "Address",
                "Tutor"),
            new Form("1 6", "1", "1", "2024"),
            new Form("2 2", "T-1", "Teacher", "Test", "test@example.invalid", "Math"),
            new Form("2 4", "MAT-1", "Math", "1"),
            new Form("2 5", "1", "1", "2024", "A", "1", "07:00", "08:00"),
            new Form("2 6", "1", "1", "2024", "1", "A"),
            new Form("2 7", "1", "1", "1", "2024", "1", "5.0"),
            new Form("2 8", "1", "1", "1", "2024", "1", "2024-02-29", "1"),
            new Form("2 9", "1"),
            new Form("2 10", "1", "1", "2024"),
            new Form("2 11", "1", "2024", "1"),
            new Form("2 12", "1", "2024", "1", "A"),
            new Form("2 14", "2024-02-29", "2024-03-01", "3"),
            new Form("3 2", "Club", "Description", "1", "2"),
            new Form("3 3", "1", "1"),
            new Form("3 4", "1", "1"),
            new Form("3 6", "Event", "Description", "2024-02-29", "Deportivo", "1"),
            new Form("3 7", "1", "1", "1er Lugar"),
            new Form("4 2", "PAY-1", "Concept", "10.00", "0.19"),
            new Form("4 3", "1", "1", "0.00"),
            new Form("4 4", "1", "1", "2024-02-29", "Reference"),
            new Form("4 5", "1", "1", "10.00", "2024-02-29", "1", "Receipt"),
            new Form("4 6", "1"),
            new Form("4 7", "1"),
            new Form("4 8", "1"),
            new Form("5 2", "RES-1", "Resource", "1", "Room"),
            new Form("5 3", "1", "1", "1", "2024-02-29"),
            new Form("5 4", "1", "1"),
            new Form("5 5", "1", "Disponible"),
            new Form("6 1 1", "1"),
            new Form("6 1 2", "1", "", "", "", "", "", "1"),
            new Form("6 1 3", "1"),
            new Form("6 1 4", "1"),
            new Form("6 1 5", "1", "", "", "", "", "", "1"),
            new Form("6 1 6", "1"),
            new Form("6 1 7", "1"),
            new Form("6 1 8", "1", "1", "Tutor", "1", "1", "1"),
            new Form("6 1 9", "1", "1"),
            new Form("6 1 10", "1"),
            new Form("6 1 11", "1", "1", "1"),
            new Form("6 1 12", "1"),
            new Form("6 2 1", "1"),
            new Form("6 2 2", "1", "", "", "", "", "", "1"),
            new Form("6 2 3", "1"),
            new Form("6 2 4", "1"),
            new Form("6 2 5", "1", "", "", "", "1"),
            new Form("6 2 6", "1"),
            new Form("6 2 8", "1", "", "", "", "", "1"),
            new Form("6 2 9", "1"),
            new Form("6 2 11", "1", "1", "07:00", "08:00"),
            new Form("6 2 12", "1"),
            new Form("6 2 13", "1"),
            new Form("6 2 14", "1", "A", "1"),
            new Form("6 2 15", "1"),
            new Form("6 2 16", "1", "1", "2024", "1"),
            new Form("6 2 17", "1"),
            new Form("6 2 18", "1"),
            new Form("6 2 19", "1", "1", "2024"),
            new Form("6 3 1", "1"),
            new Form("6 3 2", "1", "", "", "", "", ""),
            new Form("6 3 3", "1"),
            new Form("6 3 4", "1"),
            new Form("6 3 5", "1", "2024-02-29", "1"),
            new Form("6 3 6", "1"),
            new Form("6 3 7", "1", "", "", "", "1", "", ""),
            new Form("6 3 8", "1"),
            new Form("6 3 9", "1"),
            new Form("6 3 10", "1", "1", "Participante"),
            new Form("6 3 11", "1", "1"),
            new Form("6 4 2", "1", "", "", "", "", ""),
            new Form("6 4 3", "1"),
            new Form("6 4 4", "1"),
            new Form("6 4 5", "1", "1"),
            new Form("6 4 7", "1"),
            new Form("6 4 8", "1", "", "", ""),
            new Form("6 4 9", "1"),
            new Form("6 4 11", "1"),
            new Form("6 4 12", paymentEditInputs()),
            new Form("6 4 13", "1", "Reason"),
            new Form("6 4 15", "1"),
            new Form("6 4 16", paymentEditInputs()),
            new Form("6 4 17", "1"),
            new Form("6 4 18", "1", "Reason"),
            new Form("6 5 1", "1"),
            new Form("6 5 2", "1", "", "", "", ""),
            new Form("6 5 3", "1"),
            new Form("6 5 4", "1"),
            new Form("6 5 5", "1", "1"),
            new Form("6 5 6", "1", "1", "2024-02-29"),
            new Form("6 5 7", "1", "1", "Reason"),
            new Form("6 6", "1")),
        Stream.of(
            new Form("6 6 1 2", "1"),
            new Form("6 6 1 3", "Grade", "1"),
            new Form("6 6 1 4", "1", "", "", ""),
            new Form("6 6 1 5", "1"),
            new Form("6 6 2 2", "1"),
            new Form("6 6 2 3", "Category"),
            new Form("6 6 2 4", "1", "", ""),
            new Form("6 6 2 5", "1")));
  }

  private static String[] paymentEditInputs() {
    return Stream.concat(Stream.of("1"), Arrays.stream(PAYMENT_INPUTS)).toArray(String[]::new);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formInterruptions")
  void cancellationAndEofExitFromEachFormField(InterruptedForm test) {
    List<String> path = navigate(test.form.path);
    String input = lines(path) + lines(test.form.inputs.subList(0, test.fieldsCompleted));
    String output = session(input + (test.cancel ? "q\n" : ""));
    assertEquals(0, occurrences(output, RETRY), output);
    assertEquals(test.cancel ? 1 : 0, occurrences(output, CANCELLED), output);
    assertEquals(
        path.size() + test.fieldsCompleted + (test.cancel ? 2 : 1),
        occurrences(output, PROMPT),
        output);
    assertClosedCleanly(output);
  }

  private static String session(String input) {
    var bytes = new ByteArrayOutputStream();
    var view =
        new ConsoleView(
            new ByteArrayInputStream(input.getBytes(UTF_8)), new PrintStream(bytes, true, UTF_8));
    Main.run(
        view,
        CONNECTIONS,
        invoice -> {
          throw new AssertionError("An interrupted form rendered a PDF");
        });
    return bytes.toString(UTF_8);
  }

  private static void assertClosedCleanly(String output) {
    assertTrue(
        output.endsWith(
            "Fin de entrada." + System.lineSeparator() + "Hasta pronto." + System.lineSeparator()),
        output);
    assertFalse(output.contains("No se pudo completar la operación"), output);
    assertFalse(output.contains("NoSuchElementException"), output);
  }

  private static List<String> navigate(String path) {
    return path.isEmpty() ? List.of() : List.of(path.split(" "));
  }

  private static String lines(List<String> input) {
    return input.isEmpty() ? "" : String.join("\n", input) + "\n";
  }

  private static int occurrences(String output, String token) {
    return output.split(Pattern.quote(token), -1).length - 1;
  }
}
