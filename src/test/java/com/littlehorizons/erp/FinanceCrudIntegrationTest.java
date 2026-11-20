package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.unique;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.JdbcFinanceDao;
import com.littlehorizons.erp.model.GuardianLink;
import com.littlehorizons.erp.model.Invoice;
import com.littlehorizons.erp.model.PaymentConcept;
import com.littlehorizons.erp.service.FinanceService;
import com.littlehorizons.erp.util.BusinessException;
import com.littlehorizons.erp.util.SchoolInformation;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class FinanceCrudIntegrationTest extends CrudIntegrationFixture {
  @Test
  void conceptsFeesAndObligationsSupportEditAndCancellation() {
    var finance = finance();
    long guardian = guardian(), student = student(guardian);
    long concept = finance.createConcept(unique(), "Concept", money("100.00"), BigDecimal.ZERO);
    PaymentConcept c = finance.concept(concept);
    finance.updateConcept(
        new PaymentConcept(concept, c.code(), "Edited", money("120.00"), BigDecimal.ZERO, true));
    assertEquals("Edited", finance.concept(concept).name());
    finance.setFee(student, concept, money("80.00"));
    assertEquals(money("80.00"), finance.fees(student).get(0).amount());
    finance.setFee(student, concept, money("70.00"));
    assertEquals(money("70.00"), finance.fees(student).get(0).amount());
    finance.removeFee(student, concept);
    assertTrue(finance.fees(student).isEmpty());
    long obligation = finance.createObligation(student, concept, LocalDate.now(), unique());
    assertEquals(money("120.00"), finance.obligation(obligation).amount());
    finance.updateObligation(
        obligation, "Updated " + unique(), LocalDate.now().plusDays(5), money("110.00"));
    assertEquals(money("110.00"), finance.obligation(obligation).amount());
    finance.cancelObligation(obligation);
    assertEquals("Anulada", finance.obligation(obligation).status());
    assertThrows(
        BusinessException.class,
        () -> finance.confirmPayment(request(obligation, guardian, "1.00")));
    finance.deactivateConcept(concept);
    assertFalse(finance.concept(concept).active());
    assertThrows(
        BusinessException.class,
        () -> finance.createObligation(student, concept, LocalDate.now(), unique()));
  }

  @Test
  void voidingPaymentsRestoresBalanceAndMarksInvoiceFileAsCancelled() throws Exception {
    var finance = finance();
    long guardian = guardian(), student = student(guardian);
    long concept = finance.createConcept(unique(), "Concept", money("100.00"), BigDecimal.ZERO);
    long obligation =
        finance.createObligation(student, concept, LocalDate.now().minusMonths(2), unique());
    var original = finance.confirmPayment(request(obligation, guardian, "60.00"));
    assertTrue(original.fileGenerated());
    assertThrows(
        BusinessException.class,
        () -> finance.updateObligation(obligation, "Invalid", LocalDate.now(), money("59.00")));
    assertThrows(BusinessException.class, () -> finance.cancelObligation(obligation));
    var cancelled = finance.voidPayment(original.paymentId(), "Duplicate payment");
    assertTrue(cancelled.fileGenerated());
    assertEquals("Anulado", finance.payment(original.paymentId()).status());
    Invoice invoice = finance.invoice(original.invoiceId());
    assertEquals("Anulada", invoice.businessStatus());
    assertEquals(
        money("100.00"),
        finance.overdue().stream()
            .filter(b -> b.obligationId() == obligation)
            .findFirst()
            .orElseThrow()
            .balance());
    String text = TextInvoiceAssertions.validInvoice(invoice);
    assertTrue(text.contains("FACTURA ANULADA"));
    assertTrue(text.contains("Motivo de anulación: Duplicate payment"));
    assertEquals(
        original.invoiceId(), finance.voidInvoice(original.invoiceId(), "Retry").invoiceId());
    finance.cancelObligation(obligation);
    assertTrue(finance.overdue().stream().noneMatch(b -> b.obligationId() == obligation));
  }

  @Test
  void correctingPaymentIsAtomicIdempotentAndPreservesOriginal() {
    var finance = finance();
    long guardian = guardian(), student = student(guardian);
    long concept = finance.createConcept(unique(), "Concept", money("100.00"), BigDecimal.ZERO);
    long obligation =
        finance.createObligation(student, concept, LocalDate.now().minusMonths(2), unique());
    var original = finance.confirmPayment(request(obligation, guardian, "60.00"));
    var replacement = request(obligation, guardian, "80.00");
    var corrected = finance.correctPayment(original.paymentId(), replacement, "Amount corrected");
    assertTrue(corrected.fileGenerated());
    assertNotEquals(original.paymentId(), corrected.paymentId());
    assertEquals("Anulado", finance.payment(original.paymentId()).status());
    assertEquals(original.paymentId(), finance.payment(corrected.paymentId()).originalPaymentId());
    assertEquals("Anulada", finance.invoice(original.invoiceId()).businessStatus());
    assertEquals(
        corrected.paymentId(),
        finance.correctPayment(original.paymentId(), replacement, "Retry").paymentId());
    assertEquals(
        money("20.00"),
        finance.overdue().stream()
            .filter(b -> b.obligationId() == obligation)
            .findFirst()
            .orElseThrow()
            .balance());
    assertThrows(
        BusinessException.class,
        () ->
            finance.correctPayment(
                corrected.paymentId(), request(obligation, guardian, "150.00"), "Invalid amount"));
    assertEquals("Confirmado", finance.payment(corrected.paymentId()).status());
    assertEquals("Emitida", finance.invoice(corrected.invoiceId()).businessStatus());
  }

  @Test
  void failedCancelledFileRetainsReversalAndCanBeRegenerated() {
    var working = finance();
    long guardian = guardian(), student = student(guardian);
    long obligation = working.createObligation(student, 1, LocalDate.now(), unique());
    var payment = working.confirmPayment(request(obligation, guardian, "10.00"));
    var failing =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            invoice -> {
              throw new IOException("Disk failure");
            },
            SchoolInformation.fromEnvironment());
    assertFalse(failing.voidPayment(payment.paymentId(), "Reverse").fileGenerated());
    assertEquals("Anulado", working.payment(payment.paymentId()).status());
    assertEquals("Fallida", working.invoice(payment.invoiceId()).status());
    assertTrue(working.regenerate(payment.invoiceId()).fileGenerated());
    assertEquals("Anulada", working.invoice(payment.invoiceId()).businessStatus());
  }

  @Test
  void unlinkingGuardianDoesNotEraseTheirHistoricalPayments() {
    var finance = finance();
    long guardian = guardian(), student = student(guardian), other = guardian();
    long obligation = finance.createObligation(student, 1, LocalDate.now(), unique());
    var payment = finance.confirmPayment(request(obligation, guardian, "10.00"));
    students.saveLink(new GuardianLink(student, other, "Tutor", true, true, true));
    students.removeLink(student, guardian);
    assertTrue(
        finance.history(guardian, true).stream()
            .anyMatch(row -> row.startsWith(payment.paymentId() + " |")));
  }

  @Test
  void staleIssuedDocumentCannotBeMarkedGeneratedAfterCancellation() throws Exception {
    var finance = finance();
    long guardian = guardian(), student = student(guardian);
    long obligation = finance.createObligation(student, 1, LocalDate.now(), unique());
    var payment = finance.confirmPayment(request(obligation, guardian, "10.00"));
    finance.voidPayment(payment.paymentId(), "Cancel");
    try (Connection c = connections.open()) {
      c.setAutoCommit(false);
      assertFalse(
          new JdbcFinanceDao().markFileGenerated(c, payment.invoiceId(), "stale.txt", "Emitida"));
      c.rollback();
    }
    assertEquals("Anulada", finance.invoice(payment.invoiceId()).businessStatus());
  }
}
