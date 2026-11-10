package com.littlehorizons.erp.model;

public record LoanEntry(
    long id,
    long resourceId,
    long borrowerId,
    BorrowerType type,
    java.time.LocalDate borrowed,
    java.time.LocalDate due,
    java.time.LocalDate returned,
    String status) {}
