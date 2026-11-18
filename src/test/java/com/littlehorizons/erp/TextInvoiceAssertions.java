package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.model.Invoice;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

final class TextInvoiceAssertions {
  private TextInvoiceAssertions() {}

  static String validInvoice(Invoice invoice) throws Exception {
    assertEquals("Generada", invoice.status());
    Path file = Path.of(invoice.path());
    assertEquals(
        String.format(Locale.ROOT, "factura_%06d.txt", invoice.id()),
        file.getFileName().toString());
    String document = Files.readString(file, StandardCharsets.UTF_8);
    String signedFields =
        String.join(
            "|",
            invoice.number(),
            invoice.schoolNit(),
            invoice.date().toString(),
            invoice.total().toPlainString());
    String expectedCufe =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(signedFields.getBytes(StandardCharsets.UTF_8)));
    String expectedTime =
        invoice.issueTime() == null
            ? "No registrada"
            : DateTimeFormatter.ISO_LOCAL_TIME.format(invoice.issueTime());

    assertEquals(expectedCufe, invoice.cufe());
    assertTrue(document.contains("SIN VALIDACIÓN DIAN"));
    assertTrue(document.contains("CUMPLIMIENTO DIAN SIMULADO"));
    assertTrue(document.contains("Resolución de habilitación simulada: DEMO-001"));
    assertTrue(document.contains("DATOS DEL EMISOR"));
    assertTrue(document.contains("Razón social: " + invoice.schoolName()));
    assertTrue(document.contains("Teléfono: " + invoice.schoolPhone()));
    assertTrue(document.contains("Correo electrónico: " + invoice.schoolEmail()));
    assertTrue(document.contains("DATOS DEL CLIENTE (ACUDIENTE)"));
    assertTrue(document.contains("Nombre completo: " + invoice.customerName()));
    assertTrue(document.contains("Número de documento: " + invoice.customerDocument()));
    assertTrue(document.contains("Número de factura: " + invoice.number()));
    assertTrue(document.contains("Fecha de emisión: " + invoice.date()));
    assertTrue(document.contains("Hora de emisión (Bogotá): " + expectedTime));
    assertTrue(document.contains("Valor unitario"));
    assertTrue(document.contains("IVA: COP " + invoice.tax().toPlainString()));
    assertTrue(document.contains("CUFE simulado: " + expectedCufe));
    assertTrue(document.contains("CÓDIGO QR SIMULADO — CADENA DE VERIFICACIÓN"));
    String verification =
        document.lines().filter(row -> row.startsWith("SIMULACION")).findFirst().orElseThrow();
    String[] fields = verification.split("\\|", -1);
    assertEquals(7, fields.length);
    assertEquals(
        "Anulada".equals(invoice.businessStatus()) ? "SIMULACION-ANULADA" : "SIMULACION",
        fields[0]);
    assertEquals(invoice.number(), unescape(fields[1]));
    assertEquals(invoice.schoolNit(), unescape(fields[2]));
    assertEquals(invoice.date().toString(), fields[3]);
    assertEquals(expectedTime, fields[4]);
    assertEquals("COP " + invoice.total().toPlainString(), fields[5]);
    assertEquals(expectedCufe, fields[6]);
    return document;
  }

  private static String unescape(String value) {
    return value.replace("%7C", "|").replace("%25", "%");
  }
}
