package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.unique;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.Jdbc;
import com.littlehorizons.erp.dao.jdbc.JdbcFinanceDao;
import com.littlehorizons.erp.dao.jdbc.JdbcStudentDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.FinanceService;
import com.littlehorizons.erp.service.StudentService;
import com.littlehorizons.erp.util.BusinessException;
import com.littlehorizons.erp.util.SchoolInformation;
import com.littlehorizons.erp.util.TextInvoiceGenerator;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class FinanceDatabaseIntegrationTest extends DatabaseIntegrationFixture {
  @Test
  void paymentSurvivesFileFailureAndRetryDoesNotDuplicateIt() throws Exception {
    var dao = new JdbcFinanceDao();
    long student = student();
    var failing =
        new FinanceService(
            connections,
            dao,
            invoice -> {
              throw new IOException("Simulated disk failure");
            },
            SchoolInformation.fromEnvironment());
    long concept =
        failing.createConcept(
            unique(), "Test concept", new BigDecimal("100.00"), new BigDecimal("0.19"));
    failing.setFee(student, concept, new BigDecimal("80.00"));
    long obligation =
        failing.createObligation(student, concept, LocalDate.now().minusMonths(2), unique());
    String receipt = unique();
    var request =
        new PaymentRequest(
            obligation,
            1,
            new BigDecimal("95.20"),
            LocalDate.now(),
            PaymentMethod.TRANSFER,
            receipt);
    PaymentResult first = failing.confirmPayment(request);
    assertFalse(first.fileGenerated());
    assertEquals(1, count("SELECT COUNT(*) FROM pagos WHERE numero_recibo=?", receipt));
    var working =
        new FinanceService(
            connections,
            dao,
            new TextInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    PaymentResult retried = working.confirmPayment(request);
    assertTrue(retried.fileGenerated());
    assertEquals(first.paymentId(), retried.paymentId());
    assertEquals(first.invoiceId(), retried.invoiceId());
    assertEquals(1, count("SELECT COUNT(*) FROM facturas WHERE id_pago=?", first.paymentId()));
    try (Connection c = connections.open()) {
      Invoice invoice = dao.invoice(c, first.invoiceId()).orElseThrow();
      assertEquals(new BigDecimal("80.00"), invoice.subtotal());
      assertEquals(new BigDecimal("15.20"), invoice.tax());
      String text = TextInvoiceAssertions.validInvoice(invoice);
      assertTrue(text.contains("Subtotal: COP 80.00"));
      assertTrue(text.contains("IVA: COP 15.20"));
      assertTrue(text.contains("Total: COP 95.20"));
    }
    assertThrows(
        BusinessException.class,
        () ->
            working.confirmPayment(
                new PaymentRequest(
                    obligation,
                    1,
                    new BigDecimal("1.00"),
                    LocalDate.now(),
                    PaymentMethod.TRANSFER,
                    receipt)));
  }

  @Test
  void unpaidObligationAppearsInOverdueReport() {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new TextInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long obligation =
        service.createObligation(student(), 1, LocalDate.now().minusMonths(2), unique());
    assertTrue(
        service.overdue().stream()
            .anyMatch(b -> b.obligationId() == obligation && b.balance().signum() > 0));
  }

  @Test
  void concurrentPaymentsCannotExceedOutstandingBalance() throws Exception {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new TextInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long obligation = service.createObligation(student(), 1, LocalDate.now(), unique());
    List<Boolean> results =
        race(
            () ->
                service.confirmPayment(
                    new PaymentRequest(
                        obligation,
                        1,
                        new BigDecimal("350000.00"),
                        LocalDate.now(),
                        PaymentMethod.CASH,
                        unique())),
            () ->
                service.confirmPayment(
                    new PaymentRequest(
                        obligation,
                        1,
                        new BigDecimal("350000.00"),
                        LocalDate.now(),
                        PaymentMethod.CASH,
                        unique())));
    assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
    assertEquals(1, count("SELECT COUNT(*) FROM pagos WHERE id_obligacion=?", obligation));
  }

  @Test
  void invoiceInsertFailureRollsBackThePayment() throws Exception {
    SchoolInformation invalidSchool =
        new SchoolInformation("X".repeat(300), "DEMO", "Street", "Phone", "school@example.invalid");
    var service =
        new FinanceService(
            connections, new JdbcFinanceDao(), new TextInvoiceGenerator(directory), invalidSchool);
    long obligation = service.createObligation(student(), 1, LocalDate.now(), unique());
    String receipt = unique();
    assertThrows(
        BusinessException.class,
        () ->
            service.confirmPayment(
                new PaymentRequest(
                    obligation,
                    1,
                    new BigDecimal("10.00"),
                    LocalDate.now(),
                    PaymentMethod.CASH,
                    receipt)));
    assertEquals(0, count("SELECT COUNT(*) FROM pagos WHERE numero_recibo=?", receipt));
    assertEquals(0, count("SELECT COUNT(*) FROM pagos WHERE id_obligacion=?", obligation));
  }

  @Test
  void partialPaymentsSettleDebtAndInvoiceRetainsCustomerSnapshot() throws Exception {
    var students = new StudentService(connections, new JdbcStudentDao());
    long guardian =
        students.registerGuardian(
            new Guardian(0, unique(), "Original", "Guardian", "3100000000", "Original street"));
    long student =
        students.register(
            new Student(
                0, unique(), "Test", "Snapshot", LocalDate.of(2015, 1, 1), "Street", "Activo"),
            guardian,
            "Tutor");
    var dao = new JdbcFinanceDao();
    var service =
        new FinanceService(
            connections,
            dao,
            new TextInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long concept =
        service.createConcept(
            unique(), "Partial payment concept", new BigDecimal("100.00"), BigDecimal.ZERO);
    long obligation =
        service.createObligation(student, concept, LocalDate.now().minusMonths(2), unique());
    PaymentResult first =
        service.confirmPayment(
            new PaymentRequest(
                obligation,
                guardian,
                new BigDecimal("40.00"),
                LocalDate.now(),
                PaymentMethod.CASH,
                unique()));
    assertTrue(first.fileGenerated());
    assertEquals(
        new BigDecimal("60.00"),
        service.overdue().stream()
            .filter(b -> b.obligationId() == obligation)
            .findFirst()
            .orElseThrow()
            .balance());
    try (Connection c = connections.open()) {
      Jdbc.update(
          c,
          "UPDATE acudientes SET primer_nombre='Changed',direccion='Changed street' WHERE"
              + " id_acudiente=?",
          guardian);
      Invoice snapshot = dao.invoice(c, first.invoiceId()).orElseThrow();
      assertEquals("Original Guardian", snapshot.customerName());
      assertEquals("Original street", snapshot.customerAddress());
    }
    assertTrue(
        service
            .confirmPayment(
                new PaymentRequest(
                    obligation,
                    guardian,
                    new BigDecimal("60.00"),
                    LocalDate.now(),
                    PaymentMethod.CASH,
                    unique()))
            .fileGenerated());
    assertTrue(service.overdue().stream().noneMatch(b -> b.obligationId() == obligation));
  }

  @Test
  void invalidDiscountedFeesAndOverpaymentsAreRejected() throws Exception {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new TextInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long student = student();
    long concept =
        service.createConcept(
            unique(), "Discount audit", new BigDecimal("100.00"), BigDecimal.ZERO);
    assertThrows(
        BusinessException.class, () -> service.setFee(student, concept, new BigDecimal("-0.01")));
    service.setFee(student, concept, BigDecimal.ZERO);
    long free = service.createObligation(student, concept, LocalDate.now(), unique());
    assertThrows(
        BusinessException.class,
        () ->
            service.confirmPayment(
                new PaymentRequest(
                    free,
                    1,
                    new BigDecimal("0.01"),
                    LocalDate.now(),
                    PaymentMethod.CASH,
                    unique())));
    service.setFee(student, concept, new BigDecimal("70.00"));
    long discounted = service.createObligation(student, concept, LocalDate.now(), unique());
    try (Connection c = connections.open()) {
      assertEquals(
          new BigDecimal("70.00"),
          Jdbc.query(
                  c,
                  "SELECT monto FROM obligaciones WHERE id_obligacion=?",
                  r -> r.getBigDecimal(1),
                  discounted)
              .get(0));
      assertThrows(
          SQLException.class,
          () ->
              Jdbc.update(c, "UPDATE obligaciones SET monto=-1 WHERE id_obligacion=?", discounted));
      assertThrows(
          SQLException.class,
          () ->
              Jdbc.update(
                  c,
                  "UPDATE tarifas_estudiante SET monto=-1 WHERE id_estudiante=? AND id_concepto=?",
                  student,
                  concept));
    }
    assertThrows(
        BusinessException.class,
        () ->
            service.confirmPayment(
                new PaymentRequest(
                    discounted,
                    1,
                    new BigDecimal("70.01"),
                    LocalDate.now(),
                    PaymentMethod.CASH,
                    unique())));
    assertEquals(0, count("SELECT COUNT(*) FROM pagos WHERE id_obligacion=?", discounted));
  }

  @Test
  void grossPriceOverflowIsRejectedBeforeObligationInsert() throws Exception {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new TextInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long student = student();
    long concept =
        service.createConcept(
            unique(), "Overflow audit", new BigDecimal("99999999.99"), BigDecimal.ONE);
    assertThrows(
        BusinessException.class,
        () -> service.createObligation(student, concept, LocalDate.now(), unique()));
    assertEquals(
        0,
        count(
            "SELECT COUNT(*) FROM obligaciones WHERE id_estudiante=? AND id_concepto=?",
            student,
            concept));
  }

  @Test
  void chequePaymentMatchesDatabaseEnumAndIsIdempotent() throws Exception {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new TextInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long obligation = service.createObligation(student(), 1, LocalDate.now(), unique());
    var request =
        new PaymentRequest(
            obligation, 1, new BigDecimal("1.00"), LocalDate.now(), PaymentMethod.CHEQUE, unique());
    var first = service.confirmPayment(request);
    var second = service.confirmPayment(request);
    assertTrue(first.fileGenerated());
    assertTrue(second.fileGenerated());
    assertEquals(first.paymentId(), second.paymentId());
    try (Connection c = connections.open()) {
      var invoice = new JdbcFinanceDao().invoice(c, first.invoiceId()).orElseThrow();
      assertEquals(
          FinanceService.cufe(
              invoice.number(), invoice.schoolNit(), invoice.date(), invoice.total()),
          invoice.cufe());
      assertEquals(
          Path.of(invoice.path()).getFileName().toString(),
          Jdbc.query(
                  c,
                  "SELECT nombre_archivo FROM facturas WHERE id_factura=?",
                  r -> r.getString(1),
                  invoice.id())
              .get(0));
    }
  }
}
