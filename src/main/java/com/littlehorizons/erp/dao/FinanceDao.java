package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.SchoolInformation;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public interface FinanceDao {
  long createConcept(Connection c, String code, String name, BigDecimal amount, BigDecimal taxRate)
      throws SQLException;

  List<String> concepts(Connection c) throws SQLException;

  void setFee(Connection c, long student, long concept, BigDecimal amount) throws SQLException;

  Optional<FeeQuote> quote(Connection c, long student, long concept) throws SQLException;

  long createObligation(
      Connection c,
      long student,
      long concept,
      LocalDate due,
      String reference,
      BigDecimal amount,
      BigDecimal taxRate)
      throws SQLException;

  Optional<Obligation> lockObligation(Connection c, long id) throws SQLException;

  Optional<StoredPayment> findPayment(Connection c, String receipt) throws SQLException;

  Optional<Guardian> responsible(Connection c, long student, long guardian) throws SQLException;

  long insertPayment(Connection c, Obligation obligation, PaymentRequest request)
      throws SQLException;

  long insertInvoice(
      Connection c,
      long payment,
      PaymentRequest request,
      Guardian guardian,
      SchoolInformation school,
      BigDecimal subtotal,
      BigDecimal tax,
      LocalDate issueDate)
      throws SQLException;

  void identifyInvoice(Connection c, long invoice, String number, String cufe) throws SQLException;

  void insertLine(Connection c, long invoice, InvoiceLine line) throws SQLException;

  Optional<Invoice> invoice(Connection c, long id) throws SQLException;

  void pdfStatus(Connection c, long id, String status, String path) throws SQLException;

  List<Balance> balances(Connection c, LocalDate overdueBefore) throws SQLException;

  List<String> history(Connection c, long id, boolean guardian) throws SQLException;

  List<PaymentConcept> conceptEntries(Connection c) throws SQLException;

  Optional<PaymentConcept> concept(Connection c, long id, boolean lock) throws SQLException;

  int updateConcept(Connection c, PaymentConcept entry) throws SQLException;

  List<StudentFee> fees(Connection c, long student) throws SQLException;

  int deleteFee(Connection c, long student, long concept) throws SQLException;

  List<ObligationEntry> obligations(Connection c) throws SQLException;

  Optional<ObligationEntry> obligation(Connection c, long id, boolean lock) throws SQLException;

  int updateObligation(Connection c, ObligationEntry entry) throws SQLException;

  List<PaymentEntry> payments(Connection c) throws SQLException;

  Optional<PaymentEntry> payment(Connection c, long id, boolean lock) throws SQLException;

  Optional<Long> invoiceId(Connection c, long payment) throws SQLException;

  int voidPayment(Connection c, long id, String reason) throws SQLException;

  void markReplacement(Connection c, long replacement, long original) throws SQLException;

  List<Invoice> invoices(Connection c) throws SQLException;

  boolean activeStudent(Connection c, long student) throws SQLException;

  boolean markPdfGenerated(Connection c, long id, String path, String expectedState)
      throws SQLException;
}
