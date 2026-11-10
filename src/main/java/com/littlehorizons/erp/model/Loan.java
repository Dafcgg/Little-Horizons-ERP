package com.littlehorizons.erp.model;

public record Loan(
    long id,
    long resourceId,
    long borrowerId,
    BorrowerType borrowerType,
    java.time.LocalDate dueDate) {}
