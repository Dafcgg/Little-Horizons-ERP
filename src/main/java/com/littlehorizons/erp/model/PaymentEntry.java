package com.littlehorizons.erp.model;

public record PaymentEntry(
    long id,
    long studentId,
    PaymentRequest request,
    String status,
    String cancellationReason,
    Long originalPaymentId) {}
