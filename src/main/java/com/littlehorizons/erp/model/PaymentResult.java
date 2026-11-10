package com.littlehorizons.erp.model;

public record PaymentResult(long paymentId, long invoiceId, boolean pdfGenerated, String message) {}
