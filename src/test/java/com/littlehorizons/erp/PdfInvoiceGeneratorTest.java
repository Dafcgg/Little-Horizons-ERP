package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfInvoiceGeneratorTest {
  @TempDir Path directory;

  private Invoice invoice(String customer, List<InvoiceLine> lines) {
    return new Invoice(
        123,
        12,
        "LH-000123",
        LocalDate.of(2026, 11, 10),
        customer,
        "123",
        "Demo street",
        "Little Horizons DEMO",
        "DEMO-NIT",
        "School street",
        "000",
        "school@example.invalid",
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        new BigDecimal("100.00"),
        "a".repeat(64),
        "Pendiente",
        null,
        lines);
  }

  @Test
  void unsupportedCharactersAreReplacedWithoutLosingLatinNames() throws Exception {
    var invoice =
        invoice(
            "Muñoz 😀 \u0081 \u007f",
            List.of(
                new InvoiceLine(
                    "Pensión", 1, new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ZERO)));
    Path path = new PdfInvoiceGenerator(directory).render(invoice);
    try (var pdf = Loader.loadPDF(path.toFile())) {
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("Muñoz ?"));
      assertTrue(text.contains("Pensión"));
    }
  }

  @Test
  void longInvoicesPaginateWithoutDiscardingTheLastLineOrTotals() throws Exception {
    List<InvoiceLine> lines = new ArrayList<>();
    for (int i = 0; i < 100; i++)
      lines.add(
          new InvoiceLine(
              "Item-" + i + " " + "W".repeat(100),
              1,
              BigDecimal.ONE,
              BigDecimal.ONE,
              BigDecimal.ZERO));
    Path path = new PdfInvoiceGenerator(directory).render(invoice("Test", lines));
    try (var pdf = Loader.loadPDF(path.toFile())) {
      assertTrue(pdf.getNumberOfPages() > 1);
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("Item-99"));
      assertTrue(text.contains("TOTAL: COP 100.00"));
      assertTrue(text.contains("a".repeat(64)));
    }
  }

  @Test
  void failedDestinationReplacementLeavesNoTemporaryFile() throws Exception {
    Path destination = Files.createDirectory(directory.resolve("factura_000123.pdf"));
    Files.writeString(destination.resolve("keep.txt"), "keep");
    assertThrows(
        java.io.IOException.class,
        () -> new PdfInvoiceGenerator(directory).render(invoice("Test", List.of())));
    try (var files = Files.list(directory)) {
      assertEquals(List.of(destination), files.toList());
    }
  }
}
