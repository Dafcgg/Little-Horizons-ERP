package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class FinanceFilesystemIntegrationTest {
  @TempDir Path directory;
  private final JdbcFinanceDao dao = new JdbcFinanceDao();
  private final SchoolInformation school = SchoolInformation.fromEnvironment();

  @BeforeAll
  static void configureLogging() throws Exception {
    AppLogger.configure();
  }

  @RepeatedTest(100)
  void generatedInvoiceContainsMatchingCufeAndVerificationCode(RepetitionInfo repetition)
      throws Exception {
    var students = new StudentService(CONNECTIONS, new JdbcStudentDao());
    String key = unique();
    boolean maximumName = repetition.getCurrentRepetition() == 1;
    String firstName = maximumName ? "Zoë".repeat(33) + "Z" : "Zoë '🚀";
    String lastName = maximumName ? "Muñoz".repeat(20) : "Muñoz \"漢🔥";
    String secondName = maximumName ? "Á".repeat(100) : "Ángela";
    String secondLastName = maximumName ? "R".repeat(100) : "Ríos";
    long guardian =
        students.registerGuardian(
            new Guardian(0, key, firstName, lastName, "555", "Calle\\12\r\nΔ\u0000"));
    try (var connection = CONNECTIONS.open()) {
      Jdbc.update(
          connection,
          """
          UPDATE acudientes SET segundo_nombre=?,segundo_apellido=? WHERE id_acudiente=?
          """,
          secondName,
          secondLastName,
          guardian);
    }
    long student =
        students.register(
            new Student(0, key, "Student", "Invoice", LocalDate.of(2012, 2, 29), null, "Activo"),
            guardian,
            "Tutor");
    var finance = finance(new TextInvoiceGenerator(directory));
    long concept =
        finance.createConcept(
            key, "Taller \"\\; (arte) 漢🚀", new BigDecimal("100.00"), new BigDecimal("0.19"));
    long obligation = finance.createObligation(student, concept, LocalDate.now(), key);
    ZoneId schoolZone = ZoneId.of("America/Bogota");
    LocalDateTime beforeIssue = LocalDateTime.now(schoolZone).withNano(0);
    var result =
        finance.confirmPayment(
            new PaymentRequest(
                obligation,
                guardian,
                BigDecimal.valueOf(100 + repetition.getCurrentRepetition(), 2),
                LocalDate.now(),
                PaymentMethod.CASH,
                key));
    assertTrue(result.fileGenerated(), result.message());
    Invoice invoice = finance.invoice(result.invoiceId());
    assertEquals("Calle\\12\r\nΔ\u0000", invoice.customerAddress());
    assertEquals(
        String.join(" ", firstName, secondName, lastName, secondLastName), invoice.customerName());
    assertNotNull(invoice.issueTime());
    LocalDateTime issuedAt = LocalDateTime.of(invoice.date(), invoice.issueTime());
    assertFalse(issuedAt.isBefore(beforeIssue));
    assertFalse(issuedAt.isAfter(LocalDateTime.now(schoolZone)));
    String text = TextInvoiceAssertions.validInvoice(invoice);
    assertTrue(text.contains("Dirección: Calle\\12  Δ "));
    assertTrue(text.contains("Taller"));
    assertTrue(text.contains("漢🚀"));
    assertFalse(text.contains("\u0000"));
    try (var files = Files.list(directory)) {
      assertEquals(1L, files.count());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.00", "75.00", "125.00"})
  void fullScholarshipAndFeesOnBothSidesOfTheBasePrice(String value) {
    var finance = finance(new TextInvoiceGenerator(directory));
    long concept =
        finance.createConcept(
            unique(), "Fee boundary", new BigDecimal("100.00"), new BigDecimal("0.20"));
    BigDecimal fee = new BigDecimal(value);
    finance.setFee(1, concept, fee);
    long obligation =
        finance.createObligation(1, concept, LocalDate.now().minusMonths(2), unique());
    assertEquals(
        fee.multiply(new BigDecimal("1.20")).setScale(2), finance.obligation(obligation).amount());
    if (fee.signum() == 0) {
      assertTrue(
          finance.overdue().stream().noneMatch(balance -> balance.obligationId() == obligation));
      assertThrows(
          BusinessException.class,
          () ->
              finance.confirmPayment(
                  new PaymentRequest(
                      obligation,
                      1,
                      BigDecimal.ONE,
                      LocalDate.now(),
                      PaymentMethod.CASH,
                      unique())));
    }
  }

  @Test
  void actualWriteFailureIsLoggedAndLeavesOneRecoverablePayment() throws Exception {
    Path output = directory.resolve("invoices");
    Files.writeString(output, "A regular file prevents creating this output directory.");
    var finance = finance(new TextInvoiceGenerator(output));
    long obligation = newObligation(finance);
    var payment =
        finance.confirmPayment(
            new PaymentRequest(
                obligation, 1, BigDecimal.ONE, LocalDate.now(), PaymentMethod.CASH, unique()));
    assertFalse(payment.fileGenerated());
    Invoice failed = finance.invoice(payment.invoiceId());
    assertEquals("Fallida", failed.status());
    assertNull(failed.path());
    assertTrue(log().contains("Invoice rendering failed for invoice " + failed.id()));
    assertTrue(log().contains("FileAlreadyExistsException"));
    assertEquals(1, paymentsFor(obligation));
    Files.delete(output);
    assertTrue(finance.regenerate(failed.id()).fileGenerated());
    Invoice recovered = finance.invoice(failed.id());
    assertEquals(failed.cufe(), recovered.cufe());
    TextInvoiceAssertions.validInvoice(recovered);
    assertEquals(1, paymentsFor(obligation));
  }

  @Test
  void aDatabaseErrorAfterAnInsertRollsBackAndWritesTheException() throws Exception {
    var finance = finance(new TextInvoiceGenerator(directory));
    long obligation = newObligation(finance);
    String receipt = unique();
    var request =
        new PaymentRequest(
            obligation, 1, BigDecimal.ONE, LocalDate.now(), PaymentMethod.CASH, receipt);
    // Use an impossible school address for the invoice snapshot, after the payment INSERT.
    var invalidSchool =
        new SchoolInformation(
            school.name(), school.nit(), "A".repeat(256), school.phone(), school.email());
    var failing =
        new FinanceService(CONNECTIONS, dao, new TextInvoiceGenerator(directory), invalidSchool);
    BusinessException rejection =
        assertThrows(BusinessException.class, () -> failing.confirmPayment(request));
    assertInstanceOf(SQLException.class, rejection.getCause());
    assertEquals(0, paymentsFor(obligation));
    assertTrue(log().contains(rejection.getCause().getMessage()));
    assertTrue(log().contains("Database transaction failed"));
    try (var files = Files.list(directory)) {
      assertEquals(0, files.count());
    }
    var recovered = finance.confirmPayment(request);
    assertTrue(recovered.fileGenerated());
    assertEquals(1, paymentsFor(obligation));
  }

  @Test
  void businessRejectionAfterVoidingRollsBackAndLogsTheRule() throws Exception {
    var finance = finance(new TextInvoiceGenerator(directory));
    long obligation = newObligation(finance);
    var initial =
        finance.confirmPayment(
            new PaymentRequest(
                obligation, 1, BigDecimal.ONE, LocalDate.now(), PaymentMethod.CASH, unique()));
    assertTrue(initial.fileGenerated());
    var excessive =
        new PaymentRequest(
            obligation, 1, new BigDecimal("2.00"), LocalDate.now(), PaymentMethod.CASH, unique());
    var rejection =
        assertThrows(
            BusinessException.class,
            () -> finance.correctPayment(initial.paymentId(), excessive, "Mistyped amount"));
    assertEquals("El pago supera el saldo pendiente.", rejection.getMessage());
    assertTrue(log().contains("Database transaction rejected by a business rule"));
    assertTrue(log().contains("BusinessException: " + rejection.getMessage()));
    assertEquals("Confirmado", finance.payment(initial.paymentId()).status());
    Invoice invoice = finance.invoice(initial.invoiceId());
    assertEquals("Emitida", invoice.businessStatus());
    TextInvoiceAssertions.validInvoice(invoice);
    assertEquals(1, paymentsFor(obligation));
  }

  @Test
  void regenerationReplacesAHistoricalPathAndPreservesTheOriginalInvoice() throws Exception {
    var finance = finance(new TextInvoiceGenerator(directory));
    long obligation = newObligation(finance);
    var payment =
        finance.confirmPayment(
            new PaymentRequest(
                obligation, 1, BigDecimal.ONE, LocalDate.now(), PaymentMethod.CASH, unique()));
    assertTrue(payment.fileGenerated());
    Invoice original = finance.invoice(payment.invoiceId());
    Files.delete(Path.of(original.path()));
    Path historicalFile = Files.writeString(directory.resolve("legacy.txt"), "Historical artifact");
    try (var connection = CONNECTIONS.open()) {
      Jdbc.update(
          connection,
          """
          UPDATE facturas SET hora_emision=NULL,ruta_archivo=?,nombre_archivo=?
          WHERE id_factura=?
          """,
          historicalFile.toString(),
          historicalFile.getFileName().toString(),
          original.id());
    }

    PaymentResult regenerated = finance.regenerate(original.id());
    Invoice invoice = finance.invoice(original.id());
    assertTrue(regenerated.fileGenerated(), regenerated.message());
    assertEquals(original.paymentId(), regenerated.paymentId());
    assertEquals(original.number(), invoice.number());
    assertEquals(original.cufe(), invoice.cufe());
    assertEquals(original.date(), invoice.date());
    assertNull(invoice.issueTime());
    assertEquals(original.path(), invoice.path());
    assertTrue(
        TextInvoiceAssertions.validInvoice(invoice)
            .contains("Hora de emisión (Bogotá): No registrada"));
    assertEquals("Historical artifact", Files.readString(historicalFile));
    assertEquals(1, paymentsFor(obligation));
    try (var connection = CONNECTIONS.open()) {
      assertEquals(
          Path.of(invoice.path()).getFileName().toString(),
          Jdbc.query(
                  connection,
                  "SELECT nombre_archivo FROM facturas WHERE id_factura=?",
                  row -> row.getString(1),
                  invoice.id())
              .get(0));
    }
  }

  private static long newObligation(FinanceService finance) {
    long concept =
        finance.createConcept(unique(), "File boundary", BigDecimal.ONE, BigDecimal.ZERO);
    return finance.createObligation(1, concept, LocalDate.now(), unique());
  }

  private static long paymentsFor(long obligation) throws SQLException {
    try (var connection = CONNECTIONS.open()) {
      return Jdbc.count(connection, "SELECT COUNT(*) FROM pagos WHERE id_obligacion=?", obligation);
    }
  }

  private static String log() throws Exception {
    String base = System.getProperty("erp.log.path", "error_log.txt");
    StringBuilder sb = new StringBuilder();
    Path p = Path.of(base);
    if (Files.isRegularFile(p)) {
      sb.append(Files.readString(p));
    }
    Path rotated = Path.of(base + ".1");
    if (Files.isRegularFile(rotated)) {
      sb.append("\n").append(Files.readString(rotated));
    }
    return sb.toString();
  }

  private FinanceService finance(InvoiceRenderer renderer) {
    return new FinanceService(CONNECTIONS, dao, renderer, school);
  }
}
