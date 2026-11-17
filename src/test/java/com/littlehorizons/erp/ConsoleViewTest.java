package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.view.ConsoleView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConsoleViewTest {
  @Test
  void invalidInputsRetryAndMenuExits() {
    var bytes = new ByteArrayOutputStream();
    var view =
        new ConsoleView(
            new ByteArrayInputStream("oops\n-1\n0\n".getBytes(StandardCharsets.UTF_8)),
            new PrintStream(bytes));
    view.menu("Prueba");
    assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Dato inválido"));
  }

  @Test
  void cancellationAndEofAreExplicit() {
    var view =
        new ConsoleView(
            new ByteArrayInputStream("q\n".getBytes(StandardCharsets.UTF_8)), System.out);
    assertThrows(ConsoleView.Cancelled.class, () -> view.required("Nombre"));
    assertThrows(ConsoleView.EndOfInput.class, () -> view.id("ID"));
  }

  @Test
  void mainClosesCleanlyAfterSystemInputEndsDuringAForm() {
    var output = new ByteArrayOutputStream();
    var errors = new ByteArrayOutputStream();
    InputStream originalInput = System.in;
    PrintStream originalOutput = System.out;
    PrintStream originalError = System.err;
    try {
      System.setIn(
          new ByteArrayInputStream(
              "2\n7\n1\n1\n1\n2024\n1\nNaN\n".getBytes(StandardCharsets.UTF_8)));
      System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errors, true, StandardCharsets.UTF_8));
      Main.main(new String[0]);
    } finally {
      System.setIn(originalInput);
      System.setOut(originalOutput);
      System.setErr(originalError);
    }
    String console = output.toString(StandardCharsets.UTF_8);
    assertTrue(console.contains("Dato inválido"));
    assertTrue(console.contains("Fin de entrada."));
    assertTrue(console.contains("Hasta pronto."));
    assertEquals("", errors.toString(StandardCharsets.UTF_8));
  }

  @Test
  void ioFailureMidInputIsTreatedAsEndOfInput() {
    InputStream input =
        new InputStream() {
          private final ByteArrayInputStream available =
              new ByteArrayInputStream("uno\n".getBytes(StandardCharsets.UTF_8));

          @Override
          public int read() throws IOException {
            int next = available.read();
            if (next < 0) throw new IOException("Input closed while reading");
            return next;
          }
        };
    var view = new ConsoleView(input, new PrintStream(OutputStream.nullOutputStream()));
    assertEquals("uno", view.required("First field"));
    assertThrows(ConsoleView.EndOfInput.class, () -> view.required("Next field"));
  }
}
