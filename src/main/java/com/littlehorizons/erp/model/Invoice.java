package com.littlehorizons.erp.model;

public record Invoice(
    long id,
    long paymentId,
    String number,
    java.time.LocalDate date,
    String customerName,
    String customerDocument,
    String customerAddress,
    String schoolName,
    String schoolNit,
    String schoolAddress,
    String schoolPhone,
    String schoolEmail,
    java.math.BigDecimal subtotal,
    java.math.BigDecimal tax,
    java.math.BigDecimal total,
    String cufe,
    String status,
    String path,
    java.util.List<InvoiceLine> lines,
    String businessStatus,
    String cancellationReason) {
  public Invoice(
      long id,
      long paymentId,
      String number,
      java.time.LocalDate date,
      String customerName,
      String customerDocument,
      String customerAddress,
      String schoolName,
      String schoolNit,
      String schoolAddress,
      String schoolPhone,
      String schoolEmail,
      java.math.BigDecimal subtotal,
      java.math.BigDecimal tax,
      java.math.BigDecimal total,
      String cufe,
      String status,
      String path,
      java.util.List<InvoiceLine> lines) {
    this(
        id,
        paymentId,
        number,
        date,
        customerName,
        customerDocument,
        customerAddress,
        schoolName,
        schoolNit,
        schoolAddress,
        schoolPhone,
        schoolEmail,
        subtotal,
        tax,
        total,
        cufe,
        status,
        path,
        lines,
        "Emitida",
        null);
  }

  public Invoice {
    lines = java.util.List.copyOf(lines);
  }
}
