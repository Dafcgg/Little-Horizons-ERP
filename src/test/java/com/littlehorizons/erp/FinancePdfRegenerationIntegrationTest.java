package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "ERP_TEST_DB_URL", matches = ".+")
class FinancePdfRegenerationIntegrationTest {
  private final ConnectionProvider connections =
      () ->
          DriverManager.getConnection(
              System.getenv("ERP_TEST_DB_URL"),
              System.getenv().getOrDefault("ERP_TEST_DB_USER", "root"),
              System.getenv("ERP_TEST_DB_PASSWORD"));

  @TempDir Path directory;

  @Test
  void failedRegenerationChangesGeneratedInvoiceToFailedAndSuccessfulRetryRestoresIt()
      throws Exception {
    var dao = new JdbcFinanceDao();
    var service =
        new FinanceService(
            connections,
            dao,
            new PdfInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long student =
        new StudentService(connections, new JdbcStudentDao())
            .register(
                new Student(
                    0,
                    UUID.randomUUID().toString().substring(0, 12),
                    "Test",
                    "Regeneration",
                    LocalDate.of(2015, 1, 1),
                    "Test address",
                    "Activo"),
                1,
                "Tutor");
    long obligation =
        service.createObligation(student, 1, LocalDate.now(), UUID.randomUUID().toString());
    PaymentResult first =
        service.confirmPayment(
            new PaymentRequest(
                obligation,
                1,
                new BigDecimal("1.00"),
                LocalDate.now(),
                PaymentMethod.CASH,
                UUID.randomUUID().toString()));
    assertTrue(first.pdfGenerated());
    Invoice original = service.invoice(first.invoiceId());
    assertEquals("Generada", original.status());

    var failingService =
        new FinanceService(
            connections,
            dao,
            invoice -> {
              throw new IOException("Output file is locked");
            },
            SchoolInformation.fromEnvironment());
    assertFalse(failingService.regenerate(first.invoiceId()).pdfGenerated());
    Invoice failed = service.invoice(first.invoiceId());
    assertEquals("Fallida", failed.status());
    assertEquals(original.path(), failed.path());
    assertEquals("Emitida", failed.businessStatus());

    PaymentResult retried = service.regenerate(first.invoiceId());
    assertTrue(retried.pdfGenerated());
    assertEquals(first.paymentId(), retried.paymentId());
    assertEquals("Generada", service.invoice(first.invoiceId()).status());
    try (Connection c = connections.open()) {
      assertEquals(
          1, Jdbc.count(c, "SELECT COUNT(*) FROM pagos WHERE id_obligacion=?", obligation));
      assertEquals(
          1, Jdbc.count(c, "SELECT COUNT(*) FROM facturas WHERE id_pago=?", first.paymentId()));
      assertEquals(0, dao.pdfStatus(c, Long.MAX_VALUE, "Fallida", null));
    }
  }
}
