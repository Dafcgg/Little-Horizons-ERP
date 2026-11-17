package com.littlehorizons.erp.util;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.littlehorizons.erp.model.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

public final class PdfInvoiceGenerator implements InvoiceRenderer {
  private static final int QR_PIXELS = 360;
  private static final int QR_MASK_COUNT = 8;

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
              stream.drawImage(
                  LosslessFactory.createFromImage(document, readableQr(qrPayload(invoice))),
                  40,
                  40,
                  150,
                  150);
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

  private static BufferedImage readableQr(String payload) throws IOException {
    var options = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
    options.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    options.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
    var writer = new QRCodeWriter();
    var reader = new QRCodeReader();
    // Try the encoder's preferred mask first, then the explicit QR masks if detection fails.
    for (int mask = -1; mask < QR_MASK_COUNT; mask++) {
      if (mask >= 0) options.put(EncodeHintType.QR_MASK_PATTERN, mask);
      BufferedImage image;
      try {
        image =
            MatrixToImageWriter.toBufferedImage(
                writer.encode(payload, BarcodeFormat.QR_CODE, QR_PIXELS, QR_PIXELS, options));
      } catch (WriterException error) {
        throw new IOException("QR generation failed", error);
      }
      var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
      try {
        if (payload.equals(reader.decode(bitmap).getText())) return image;
      } catch (ReaderException ignored) {
        // Some valid QR patterns are missed by the detector. Try the next mask.
      }
    }
    throw new IOException("Could not generate a readable invoice QR code");
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
