package com.littlehorizons.erp.model;

public record PaymentRequest(
    long obligationId,
    long guardianId,
    java.math.BigDecimal amount,
    java.time.LocalDate date,
    PaymentMethod method,
    String receipt) {}
