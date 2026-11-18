package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.InvoiceLine;
import com.littlehorizons.erp.service.FinanceService;
import com.littlehorizons.erp.util.TextInvoiceGenerator;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextInvoiceGeneratorTest {
  @TempDir Path directory;

  private Invoice invoice(String customer, LocalTime issueTime, List<InvoiceLine> lines) {
    String number = "LH-000123";
    String nit = "900000000-0 (SIMULADO) | 1%";
    LocalDate date = LocalDate.of(2026, 11, 10);
    BigDecimal subtotal = new BigDecimal("100.00");
    BigDecimal tax = new BigDecimal("19.00");
    BigDecimal total = subtotal.add(tax);
    return new Invoice(
        123,
        12,
        number,
        date,
        issueTime,
        customer,
        "123",
        "Calle 12",
        "Colegio Internacional Little Horizons S.A.S.",
        nit,
        "Calle 1",
        "000",
        "school@example.invalid",
        subtotal,
        tax,
        total,
        FinanceService.cufe(number, nit, date, total),
        "Generada",
        directory.resolve("factura_000123.txt").toString(),
        lines);
  }

  @Test
  void utf8PreservesNamesAndRemovesControlsThatWouldBreakTheLayout() throws Exception {
    Invoice invoice = invoice("Muñoz 😀 \u0081\r\n 續", LocalTime.NOON, List.of());
    Path path = new TextInvoiceGenerator(directory).render(invoice);
    String text = Files.readString(path, StandardCharsets.UTF_8);
    assertTrue(text.contains("Nombre completo: Muñoz 😀     續"));
    assertFalse(text.contains("\u0081"));
    assertFalse(text.contains("\r"));
  }

  @Test
  void longDescriptionsWrapAndKeepMoneyColumnsAligned() throws Exception {
    BigDecimal price = new BigDecimal("25.00");
    BigDecimal subtotal = new BigDecimal("50.00");
    String description = "Pensión " + "W".repeat(105) + "漢🚀";
    var line = new InvoiceLine(description, 2, price, subtotal, BigDecimal.ZERO);
    Invoice invoice = invoice("Zoë", LocalTime.NOON, List.of(line));
    new TextInvoiceGenerator(directory).render(invoice);
    String text = TextInvoiceAssertions.validInvoice(invoice);
    var rows = text.lines().filter(row -> row.startsWith("|")).toList();
    assertEquals(4, rows.size());
    assertTrue(rows.get(1).contains("|          2 |        COP 25.00 |        COP 50.00 |"));
    assertTrue(rows.get(3).contains("漢🚀"));
    assertTrue(rows.stream().allMatch(row -> row.startsWith("|") && row.endsWith("|")));
    assertTrue(text.contains("Dirección: Calle 12"));
    assertTrue(text.contains("Dirección: Calle 1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"00:00:00", "00:00:01", "09:01:08", "12:00:00", "19:31:59", "23:59:59"})
  void issueTimeIsWrittenWithSecondsAndEncodedInTheVerificationString(String value)
      throws Exception {
    Invoice invoice = invoice("Test customer", LocalTime.parse(value), List.of());
    Path output = new TextInvoiceGenerator(directory).render(invoice);
    assertEquals(directory.resolve("factura_000123.txt"), output);
    String text = TextInvoiceAssertions.validInvoice(invoice);
    assertTrue(text.contains("Hora de emisión (Bogotá): " + value));
    assertTrue(text.contains("|900000000-0 (SIMULADO) %7C 1%25|"));
  }

  @Test
  void failedDestinationReplacementLeavesNoTemporaryFile() throws Exception {
    Path destination = Files.createDirectory(directory.resolve("factura_000123.txt"));
    Path marker = Files.writeString(destination.resolve("keep.txt"), "keep");
    assertThrows(
        IOException.class,
        () ->
            new TextInvoiceGenerator(directory).render(invoice("Test", LocalTime.NOON, List.of())));
    assertEquals("keep", Files.readString(marker));
    try (var files = Files.list(directory)) {
      assertEquals(List.of(destination), files.toList());
    }
  }
}
