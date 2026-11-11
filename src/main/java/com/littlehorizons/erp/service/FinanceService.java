package com.littlehorizons.erp.service;

import com.littlehorizons.erp.dao.FinanceDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

public final class FinanceService extends TransactionalService {
  private final FinanceDao dao;
  private final InvoiceRenderer renderer;
  private final SchoolInformation school;

  public FinanceService(
      ConnectionProvider connections,
      FinanceDao dao,
      InvoiceRenderer renderer,
      SchoolInformation school) {
    super(connections);
    this.dao = dao;
    this.renderer = renderer;
    this.school = school;
  }

  public static BigDecimal money(BigDecimal amount) {
    require(
        amount != null
            && amount.signum() >= 0
            && amount.compareTo(new BigDecimal("99999999.99")) <= 0
            && amount.stripTrailingZeros().scale() <= 2,
        "Importe inválido; máximo dos decimales.");
    return amount.setScale(2, RoundingMode.UNNECESSARY);
  }

  public long createConcept(String code, String name, BigDecimal amount, BigDecimal rate) {
    text(code, "Código");
    text(name, "Nombre");
    BigDecimal normalized = money(amount);
    validateTaxRate(rate);
    return transaction(c -> dao.createConcept(c, code, name, normalized, rate));
  }

  private static void validateTaxRate(BigDecimal rate) {
    require(
        rate != null
            && rate.signum() >= 0
            && rate.compareTo(BigDecimal.ONE) <= 0
            && rate.stripTrailingZeros().scale() <= 4,
        "Tasa de IVA inválida.");
  }

  public List<String> concepts() {
    return transaction(dao::concepts);
  }

  public void setFee(long student, long concept, BigDecimal amount) {
    BigDecimal normalized = money(amount);
    transaction(
        c -> {
          require(dao.activeStudent(c, student), "Estudiante inactivo o inexistente.");
          require(
              dao.concept(c, concept, true)
                  .orElseThrow(() -> new BusinessException("Concepto inexistente."))
                  .active(),
              "Concepto inactivo.");
          dao.setFee(c, student, concept, normalized);
          return null;
        });
  }

  public long createObligation(long student, long concept, LocalDate due, String reference) {
    require(due != null, "Vencimiento obligatorio.");
    String normalizedReference = text(reference, "Referencia del cobro");
    require(normalizedReference.length() <= 50, "La referencia admite máximo 50 caracteres.");
    return transaction(
        c -> {
          require(dao.activeStudent(c, student), "Estudiante inactivo o inexistente.");
          FeeQuote quote =
              dao.quote(c, student, concept)
                  .orElseThrow(() -> new BusinessException("Concepto no disponible."));
          BigDecimal net = money(quote.amount());
          validateTaxRate(quote.taxRate());
          BigDecimal gross =
              money(
                  net.multiply(BigDecimal.ONE.add(quote.taxRate()))
                      .setScale(2, RoundingMode.HALF_UP));
          return dao.createObligation(
              c, student, concept, due, normalizedReference, gross, quote.taxRate());
        });
  }

  private PaymentRequest normalizePayment(PaymentRequest input) {
    require(input != null, "Datos del pago obligatorios.");
    BigDecimal amount = money(input.amount());
    require(amount.signum() > 0, "El pago debe ser positivo.");
    require(
        input.date() != null && !input.date().isAfter(LocalDate.now()) && input.method() != null,
        "Fecha o método inválido.");
    String receipt = text(input.receipt(), "Recibo");
    require(receipt.length() <= 50, "El recibo admite máximo 50 caracteres.");
    PaymentRequest request =
        new PaymentRequest(
            input.obligationId(),
            input.guardianId(),
            amount,
            input.date(),
            input.method(),
            receipt);
    return request;
  }

  public PaymentResult confirmPayment(PaymentRequest input) {
    PaymentRequest request = normalizePayment(input);
    StoredPayment stored = transaction(c -> savePayment(c, request));
    return generate(stored.id(), stored.invoiceId());
  }

  private StoredPayment savePayment(java.sql.Connection c, PaymentRequest request)
      throws java.sql.SQLException {
    BigDecimal amount = request.amount();
    String receipt = request.receipt();
    LocalDate issueDate = LocalDate.now();
    Obligation obligation =
        dao.lockObligation(c, request.obligationId())
            .orElseThrow(() -> new BusinessException("Obligación inexistente."));
    require(
        dao.obligation(c, request.obligationId(), true).orElseThrow().status().equals("Activa"),
        "La obligación está anulada.");
    Optional<StoredPayment> existing = dao.findPayment(c, receipt);
    if (existing.isPresent()) {
      require(
          samePayment(existing.get().request(), request),
          "El recibo ya existe con datos diferentes.");
      require(
          dao.payment(c, existing.get().id(), true).orElseThrow().status().equals("Confirmado"),
          "El recibo corresponde a un pago anulado; use un recibo nuevo.");
      return existing.get();
    }
    Guardian guardian =
        dao.responsible(c, obligation.studentId(), request.guardianId())
            .orElseThrow(
                () -> new BusinessException("Acudiente no autorizado para este estudiante."));
    require(
        amount.compareTo(obligation.amount().subtract(obligation.paid())) <= 0,
        "El pago supera el saldo pendiente.");
    BigDecimal subtotal =
        amount.divide(BigDecimal.ONE.add(obligation.taxRate()), 2, RoundingMode.HALF_UP);
    BigDecimal tax = amount.subtract(subtotal);
    long payment = dao.insertPayment(c, obligation, request);
    long invoice =
        dao.insertInvoice(c, payment, request, guardian, school, subtotal, tax, issueDate);
    String number = "LH-%06d".formatted(invoice);
    dao.identifyInvoice(c, invoice, number, cufe(number, school.nit(), issueDate, amount));
    dao.insertLine(
        c,
        invoice,
        new InvoiceLine("Abono: " + obligation.description(), 1, subtotal, subtotal, tax));
    return new StoredPayment(payment, invoice, request);
  }

  private static boolean samePayment(PaymentRequest left, PaymentRequest right) {
    return left.obligationId() == right.obligationId()
        && left.guardianId() == right.guardianId()
        && left.amount().compareTo(right.amount()) == 0
        && left.date().equals(right.date())
        && left.method() == right.method();
  }

  public PaymentResult regenerate(long invoiceId) {
    Invoice invoice =
        transaction(
            c ->
                dao.invoice(c, invoiceId)
                    .orElseThrow(() -> new BusinessException("Factura inexistente.")));
    return generate(invoice.paymentId(), invoiceId);
  }

  // Payment and invoice are already committed. File failures must never replay that transaction.
  private static final Object[] PDF_LOCKS =
      java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

  private PaymentResult generate(long payment, long invoiceId) {
    synchronized (PDF_LOCKS[(int) (invoiceId % PDF_LOCKS.length)]) {
      return generateLocked(payment, invoiceId);
    }
  }

  private PaymentResult generateLocked(long payment, long invoiceId) {
    try {
      Invoice invoice =
          transaction(
              c ->
                  dao.invoice(c, invoiceId)
                      .orElseThrow(() -> new BusinessException("Factura inexistente.")));
      String path = renderer.render(invoice).toString();
      transaction(
          c -> {
            require(
                dao.markPdfGenerated(c, invoiceId, path, invoice.businessStatus()),
                "La factura cambió durante la generación; regenere el documento.");
            return null;
          });
      return new PaymentResult(
          payment,
          invoiceId,
          true,
          (invoice.businessStatus().equals("Anulada")
                  ? "Anulación confirmada. PDF: "
                  : "Pago confirmado. PDF: ")
              + path);
    } catch (Exception error) {
      AppLogger.error("Invoice rendering failed for invoice " + invoiceId, error);
      try {
        transaction(
            c -> {
              dao.pdfStatus(c, invoiceId, "Fallida", null);
              return null;
            });
      } catch (RuntimeException statusError) {
        AppLogger.error("Invoice failure status could not be stored", statusError);
      }
      return new PaymentResult(
          payment,
          invoiceId,
          false,
          "Operación financiera confirmada. PDF pendiente; regenere la factura "
              + invoiceId
              + ". No registre otro pago.");
    }
  }

  public static String cufe(String number, String nit, LocalDate date, BigDecimal total) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (number + "|" + nit + "|" + date + "|" + total.toPlainString())
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  public List<Balance> overdue() {
    return transaction(c -> dao.balances(c, LocalDate.now().minusMonths(1)));
  }

  public List<String> history(long id, boolean guardian) {
    return transaction(c -> dao.history(c, id, guardian));
  }

  public List<PaymentConcept> conceptEntries() {
    return transaction(dao::conceptEntries);
  }

  public PaymentConcept concept(long id) {
    return transaction(
        c ->
            dao.concept(c, id, false)
                .orElseThrow(() -> new BusinessException("Concepto inexistente.")));
  }

  public void updateConcept(PaymentConcept entry) {
    require(entry != null, "Concepto obligatorio.");
    text(entry.code(), "Código");
    text(entry.name(), "Nombre");
    money(entry.amount());
    validateTaxRate(entry.taxRate());
    transaction(
        c -> {
          dao.concept(c, entry.id(), true)
              .orElseThrow(() -> new BusinessException("Concepto inexistente."));
          dao.updateConcept(c, entry);
          return null;
        });
  }

  public void deactivateConcept(long id) {
    transaction(
        c -> {
          PaymentConcept v =
              dao.concept(c, id, true)
                  .orElseThrow(() -> new BusinessException("Concepto inexistente."));
          dao.updateConcept(
              c, new PaymentConcept(id, v.code(), v.name(), v.amount(), v.taxRate(), false));
          return null;
        });
  }

  public List<StudentFee> fees(long student) {
    return transaction(c -> dao.fees(c, student));
  }

  public void removeFee(long student, long concept) {
    transaction(
        c -> {
          require(dao.deleteFee(c, student, concept) == 1, "Tarifa inexistente.");
          return null;
        });
  }

  public List<ObligationEntry> obligations() {
    return transaction(dao::obligations);
  }

  public ObligationEntry obligation(long id) {
    return transaction(
        c ->
            dao.obligation(c, id, false)
                .orElseThrow(() -> new BusinessException("Obligación inexistente.")));
  }

  public void updateObligation(long id, String reference, LocalDate due, BigDecimal amount) {
    text(reference, "Referencia");
    require(reference.length() <= 50 && due != null, "Referencia o vencimiento inválido.");
    BigDecimal normalized = money(amount);
    transaction(
        c -> {
          Obligation old =
              dao.lockObligation(c, id)
                  .orElseThrow(() -> new BusinessException("Obligación inexistente."));
          ObligationEntry entry = dao.obligation(c, id, true).orElseThrow();
          require(entry.status().equals("Activa"), "La obligación está anulada.");
          require(
              normalized.compareTo(old.paid()) >= 0,
              "El importe no puede ser menor que los abonos confirmados.");
          dao.updateObligation(
              c,
              new ObligationEntry(
                  id,
                  entry.studentId(),
                  entry.conceptId(),
                  reference.trim(),
                  due,
                  normalized,
                  entry.taxRate(),
                  entry.status()));
          return null;
        });
  }

  public void cancelObligation(long id) {
    transaction(
        c -> {
          Obligation old =
              dao.lockObligation(c, id)
                  .orElseThrow(() -> new BusinessException("Obligación inexistente."));
          require(old.paid().signum() == 0, "Anule primero los pagos confirmados.");
          ObligationEntry v = dao.obligation(c, id, true).orElseThrow();
          dao.updateObligation(
              c,
              new ObligationEntry(
                  id,
                  v.studentId(),
                  v.conceptId(),
                  v.reference(),
                  v.dueDate(),
                  v.amount(),
                  v.taxRate(),
                  "Anulada"));
          return null;
        });
  }

  public List<PaymentEntry> payments() {
    return transaction(dao::payments);
  }

  public PaymentEntry payment(long id) {
    return transaction(
        c ->
            dao.payment(c, id, false)
                .orElseThrow(() -> new BusinessException("Pago inexistente.")));
  }

  public List<Invoice> invoices() {
    return transaction(dao::invoices);
  }

  public Invoice invoice(long id) {
    return transaction(
        c -> dao.invoice(c, id).orElseThrow(() -> new BusinessException("Factura inexistente.")));
  }

  public PaymentResult voidPayment(long id, String reason) {
    String normalized = text(reason, "Motivo");
    require(normalized.length() <= 255, "Motivo demasiado largo.");
    PaymentEntry pointer = payment(id);
    long invoiceId =
        transaction(
            c -> {
              dao.lockObligation(c, pointer.request().obligationId())
                  .orElseThrow(() -> new BusinessException("Obligación inexistente."));
              dao.payment(c, id, true).orElseThrow();
              dao.voidPayment(c, id, normalized);
              return dao.invoiceId(c, id)
                  .orElseThrow(() -> new BusinessException("Factura inexistente."));
            });
    return generate(id, invoiceId);
  }

  public PaymentResult voidInvoice(long id, String reason) {
    return voidPayment(invoice(id).paymentId(), reason);
  }

  public PaymentResult correctPayment(long id, PaymentRequest replacement, String reason) {
    PaymentRequest request = normalizePayment(replacement);
    String normalized = text(reason, "Motivo");
    require(normalized.length() <= 255, "Motivo demasiado largo.");
    PaymentEntry pointer = payment(id);
    require(
        pointer.request().obligationId() == request.obligationId(),
        "La corrección conserva la obligación; anule y registre por separado para trasladar un"
            + " pago.");
    StoredPayment stored =
        transaction(
            c -> {
              dao.lockObligation(c, request.obligationId()).orElseThrow();
              PaymentEntry old = dao.payment(c, id, true).orElseThrow();
              var previous = dao.findPayment(c, request.receipt());
              if (previous.isPresent()) {
                PaymentEntry existing = dao.payment(c, previous.get().id(), true).orElseThrow();
                require(
                    Objects.equals(existing.originalPaymentId(), id)
                        && existing.status().equals("Confirmado")
                        && samePayment(existing.request(), request),
                    "Recibo utilizado en otra operación.");
                return previous.get();
              }
              require(old.status().equals("Confirmado"), "El pago original ya está anulado.");
              dao.voidPayment(c, id, normalized);
              StoredPayment created = savePayment(c, request);
              dao.markReplacement(c, created.id(), id);
              return created;
            });
    long originalInvoice = transaction(c -> dao.invoiceId(c, id).orElseThrow());
    PaymentResult oldDocument = generate(id, originalInvoice);
    PaymentResult result = generate(stored.id(), stored.invoiceId());
    if (!oldDocument.pdfGenerated())
      return new PaymentResult(
          result.paymentId(),
          result.invoiceId(),
          result.pdfGenerated(),
          result.message() + " La factura anulada " + originalInvoice + " requiere regeneración.");
    return result;
  }

  public PaymentResult correctInvoice(long id, PaymentRequest replacement, String reason) {
    return correctPayment(invoice(id).paymentId(), replacement, reason);
  }
}
