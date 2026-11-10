package com.littlehorizons.erp.model;

import java.math.BigDecimal;

public record Obligation(
    long id,
    long studentId,
    long conceptId,
    String description,
    BigDecimal amount,
    BigDecimal taxRate,
    BigDecimal paid) {}
