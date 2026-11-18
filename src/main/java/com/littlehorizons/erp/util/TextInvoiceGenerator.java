package com.littlehorizons.erp.util;

import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.InvoiceLine;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class TextInvoiceGenerator implements InvoiceRenderer {
  private static final int DESCRIPTION_WIDTH = 42;
  private static final String TABLE_BORDER =
      "+"
          + "-".repeat(DESCRIPTION_WIDTH + 2)
          + "+------------+------------------+------------------+";
  private static final String SECTION_BORDER = "-".repeat(TABLE_BORDER.length());
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
  private static final Pattern GRAPHEME = Pattern.compile("\\X");

  private final Path directory;

  public TextInvoiceGenerator(Path directory) {
    this.directory = directory.toAbsolutePath().normalize();
  }

  @Override
  public Path render(Invoice invoice) throws IOException {
    Files.createDirectories(directory);
    Path destination =
        directory.resolve(String.format(Locale.ROOT, "factura_%06d.txt", invoice.id()));
    Path temporary = Files.createTempFile(directory, "invoice-", ".tmp");
    try {
      Files.writeString(temporary, format(invoice), StandardCharsets.UTF_8);
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

  private static String format(Invoice invoice) {
    boolean cancelled = "Anulada".equals(invoice.businessStatus());
    String time =
        invoice.issueTime() == null ? "No registrada" : TIME_FORMAT.format(invoice.issueTime());
    var document = new StringBuilder();
    document.append(SECTION_BORDER).append('\n');
    document.append("FACTURA ELECTRÓNICA DE DEMOSTRACIÓN — SIN VALIDACIÓN DIAN\n");
    document.append(SECTION_BORDER).append('\n');
    if (cancelled) {
      document.append("FACTURA ANULADA\n");
      field(document, "Motivo de anulación", invoice.cancellationReason());
    }
    field(document, "Número de factura", invoice.number());
    field(document, "Fecha de emisión", invoice.date().toString());
    field(document, "Hora de emisión (Bogotá)", time);

    document.append('\n').append("DATOS DEL EMISOR\n");
    field(document, "Razón social", invoice.schoolName());
    field(document, "NIT", invoice.schoolNit());
    field(document, "Dirección", invoice.schoolAddress());
    field(document, "Teléfono", invoice.schoolPhone());
    field(document, "Correo electrónico", invoice.schoolEmail());

    document.append('\n').append("DATOS DEL CLIENTE (ACUDIENTE)\n");
    field(document, "Nombre completo", invoice.customerName());
    field(document, "Número de documento", invoice.customerDocument());
    field(document, "Dirección", invoice.customerAddress());

    document.append('\n').append("DETALLE DE LA FACTURA\n");
    document.append(TABLE_BORDER).append('\n');
    String descriptionHeading = "Descripción";
    tableRow(
        document,
        descriptionHeading,
        descriptionHeading.length(),
        "Cantidad",
        "Valor unitario",
        "Subtotal");
    document.append(TABLE_BORDER).append('\n');
    for (InvoiceLine line : invoice.lines()) {
      detail(document, line);
      document.append(TABLE_BORDER).append('\n');
    }
    field(document, "Subtotal", pesos(invoice.subtotal()));
    field(document, "IVA", pesos(invoice.tax()));
    field(document, "Total", pesos(invoice.total()));

    document.append('\n').append("CUMPLIMIENTO DIAN SIMULADO\n");
    field(document, "Resolución de habilitación simulada", "DEMO-001");
    field(document, "CUFE simulado", invoice.cufe());
    document.append("CÓDIGO QR SIMULADO — CADENA DE VERIFICACIÓN\n");
    document.append(
        String.join(
            "|",
            cancelled ? "SIMULACION-ANULADA" : "SIMULACION",
            verificationField(invoice.number()),
            verificationField(invoice.schoolNit()),
            invoice.date().toString(),
            time,
            pesos(invoice.total()),
            verificationField(invoice.cufe())));
    return document.append('\n').append(SECTION_BORDER).append('\n').toString();
  }

  private static void detail(StringBuilder document, InvoiceLine line) {
    var clusters = GRAPHEME.matcher(singleLine(line.description()));
    var description = new StringBuilder();
    String quantity = Integer.toString(line.quantity());
    String price = pesos(line.unitPrice());
    String subtotal = pesos(line.subtotal());
    int width = 0;
    while (clusters.find()) {
      String cluster = clusters.group();
      int columns = displayWidth(cluster);
      if (width + columns > DESCRIPTION_WIDTH) {
        tableRow(document, description.toString(), width, quantity, price, subtotal);
        description.setLength(0);
        width = 0;
        quantity = price = subtotal = "";
      }
      description.append(cluster);
      width += columns;
    }
    tableRow(document, description.toString(), width, quantity, price, subtotal);
  }

  private static void tableRow(
      StringBuilder document,
      String description,
      int width,
      String quantity,
      String price,
      String subtotal) {
    document.append(
        String.format(
            Locale.ROOT,
            "| %s%s | %10s | %16s | %16s |\n",
            description,
            " ".repeat(DESCRIPTION_WIDTH - width),
            quantity,
            price,
            subtotal));
  }

  private static int displayWidth(String cluster) {
    // Keep the money columns aligned when descriptions use common wide glyphs.
    int first = cluster.codePointAt(0);
    boolean wideGlyph =
        (first >= 0x1100 && first <= 0x115f)
            || (first >= 0x2e80 && first <= 0xa4cf)
            || (first >= 0xac00 && first <= 0xd7a3)
            || (first >= 0xf900 && first <= 0xfaff)
            || (first >= 0xff01 && first <= 0xff60)
            || (first >= 0x1f000 && first <= 0x1faff)
            || (first >= 0x20000 && first <= 0x3fffd);
    return wideGlyph || cluster.indexOf('\ufe0f') >= 0 ? 2 : 1;
  }

  private static String pesos(BigDecimal amount) {
    return "COP " + amount.toPlainString();
  }

  private static void field(StringBuilder document, String label, String value) {
    document.append(label).append(": ").append(singleLine(value)).append('\n');
  }

  private static String verificationField(String value) {
    return singleLine(value).replace("%", "%25").replace("|", "%7C");
  }

  private static String singleLine(String value) {
    var clean = new StringBuilder();
    Objects.toString(value, "")
        .codePoints()
        .forEach(
            cp ->
                clean.appendCodePoint(
                    Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029 ? ' ' : cp));
    return clean.toString();
  }
}
