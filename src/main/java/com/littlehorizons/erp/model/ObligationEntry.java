package com.littlehorizons.erp.model;

public record ObligationEntry(
    long id,
    long studentId,
    long conceptId,
    String reference,
    java.time.LocalDate dueDate,
    java.math.BigDecimal amount,
    java.math.BigDecimal taxRate,
    String status) {}
