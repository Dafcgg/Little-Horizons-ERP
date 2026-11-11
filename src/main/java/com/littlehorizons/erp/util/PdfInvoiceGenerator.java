package com.littlehorizons.erp.util;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.littlehorizons.erp.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

public final class PdfInvoiceGenerator implements InvoiceRenderer {
  private final Path directory;

  public PdfInvoiceGenerator(Path directory) {
    this.directory = directory.toAbsolutePath().normalize();
  }

  public Path render(Invoice invoice) throws IOException {
    Files.createDirectories(directory);
    Path destination = directory.resolve("factura_%06d.pdf".formatted(invoice.id()));
    Path temporary = Files.createTempFile(directory, "invoice-", ".tmp");
    try {
      try (PDDocument document = new PDDocument()) {
        var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        List<String> lines =
            new ArrayList<>(
                List.of(
                    invoice.schoolName(),
                    "NIT: " + invoice.schoolNit(),
                    invoice.schoolAddress(),
                    invoice.schoolPhone() + " | " + invoice.schoolEmail(),
                    "FACTURA DE DEMOSTRACION - SIN VALIDACION DIAN",
                    "Resolucion SIMULADA DEMO-001",
                    "Factura: " + invoice.number() + " | Fecha: " + invoice.date(),
                    "Responsable: " + invoice.customerName(),
                    "Documento: " + invoice.customerDocument(),
                    "Direccion: " + invoice.customerAddress(),
                    " ",
                    "Concepto | Cantidad | Valor unitario | Subtotal"));
        if (invoice.businessStatus().equals("Anulada")) {
          lines.add(0, "FACTURA ANULADA - " + Objects.toString(invoice.cancellationReason(), ""));
        }
        for (InvoiceLine line : invoice.lines()) {
          lines.add(
              line.description()
                  + " | "
                  + line.quantity()
                  + " | COP "
                  + line.unitPrice()
                  + " | COP "
                  + line.subtotal());
        }
        lines.addAll(
            List.of(
                " ",
                "Subtotal: COP " + invoice.subtotal(),
                "IVA: COP " + invoice.tax(),
                "TOTAL: COP " + invoice.total(),
                "CUFE SIMULADO: " + invoice.cufe()));
        List<String> rows = new ArrayList<>();
        for (String line : lines) rows.addAll(wrap(line, font));
        // Reserve the bottom of every page for a footer and the final-page QR.
        int rowsPerPage = 34;
        for (int first = 0; first < rows.size(); first += rowsPerPage) {
          PDPage page = new PDPage();
          document.addPage(page);
          try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(font, 10);
            stream.setLeading(15);
            stream.newLineAtOffset(40, 750);
            int last = Math.min(first + rowsPerPage, rows.size());
            for (int index = first; index < last; index++) {
              stream.showText(rows.get(index));
              stream.newLine();
            }
            stream.endText();
            if (last == rows.size()) {
              try {
                var matrix =
                    new QRCodeWriter()
                        .encode(
                            qrPayload(invoice),
                            BarcodeFormat.QR_CODE,
                            220,
                            220,
                            Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
                stream.drawImage(
                    LosslessFactory.createFromImage(
                        document, MatrixToImageWriter.toBufferedImage(matrix)),
                    40,
                    40,
                    150,
                    150);
              } catch (WriterException error) {
                throw new IOException("QR generation failed", error);
              }
            }
          }
        }
        document.save(temporary.toFile());
      }
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
      return destination;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public static String qrPayload(Invoice invoice) {
    return (invoice.businessStatus().equals("Anulada") ? "SIMULACION-ANULADA|" : "SIMULACION|")
        + invoice.number()
        + "|"
        + invoice.schoolNit()
        + "|"
        + invoice.date()
        + "|COP "
        + invoice.total()
        + "|"
        + invoice.cufe();
  }

  private static List<String> wrap(String text, PDType1Font font) throws IOException {
    StringBuilder safe = new StringBuilder();
    for (int codePoint : Objects.toString(text, "").codePoints().toArray()) {
      String character = new String(Character.toChars(codePoint));
      if (Character.isISOControl(codePoint)) character = " ";
      try {
        font.encode(character);
      } catch (IllegalArgumentException unsupported) {
        character = "?";
      }
      safe.append(character);
    }
    List<String> rows = new ArrayList<>();
    StringBuilder row = new StringBuilder();
    float width = 0;
    for (int index = 0; index < safe.length(); index++) {
      String character = safe.substring(index, index + 1);
      float advance = font.getStringWidth(character) / 1000 * 10;
      if (width + advance > 530 && !row.isEmpty()) {
        rows.add(row.toString());
        row.setLength(0);
        width = 0;
      }
      row.append(character);
      width += advance;
    }
    rows.add(row.toString());
    return rows;
  }
}
