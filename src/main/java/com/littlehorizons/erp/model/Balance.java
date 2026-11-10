package com.littlehorizons.erp.model;

public record Balance(
    long obligationId,
    long studentId,
    String concept,
    java.time.LocalDate dueDate,
    java.math.BigDecimal balance) {}
