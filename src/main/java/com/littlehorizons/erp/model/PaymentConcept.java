package com.littlehorizons.erp.model;

public record PaymentConcept(
    long id,
    String code,
    String name,
    java.math.BigDecimal amount,
    java.math.BigDecimal taxRate,
    boolean active) {}
