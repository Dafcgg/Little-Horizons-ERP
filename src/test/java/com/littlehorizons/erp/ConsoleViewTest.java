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
}
