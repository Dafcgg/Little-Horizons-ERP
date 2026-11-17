package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.util.PdfInvoiceGenerator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

final class PdfAssertions {
  private PdfAssertions() {}

  static void validInvoice(Invoice invoice) throws Exception {
    assertEquals("Generada", invoice.status());
    Path file = Path.of(invoice.path());
    assertTrue(Files.size(file) > 0);
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
    assertTrue(invoice.cufe().matches("[0-9a-f]{64}"));
    assertEquals(expectedCufe, invoice.cufe());
    try (var document = Loader.loadPDF(file.toFile())) {
      assertTrue(document.getNumberOfPages() > 0);
      assertTrue(new PDFTextStripper().getText(document).contains(expectedCufe));
      var decodedImages = new ArrayList<String>();
      for (var page : document.getPages()) {
        var resources = page.getResources();
        for (var name : resources.getXObjectNames()) {
          if (resources.getXObject(name) instanceof PDImageXObject image) {
            var source = new BufferedImageLuminanceSource(image.getImage());
            decodedImages.add(
                new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText());
          }
        }
      }
      assertEquals(java.util.List.of(PdfInvoiceGenerator.qrPayload(invoice)), decodedImages);
      assertTrue(decodedImages.get(0).endsWith("|" + expectedCufe));
    }
  }
}
