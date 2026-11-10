package com.littlehorizons.erp.model;

public record StoredPayment(long id, long invoiceId, PaymentRequest request) {}
