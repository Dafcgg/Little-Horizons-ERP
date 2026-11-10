package com.littlehorizons.erp.model;

public record AcademicRecord(
    long studentId,
    long subjectId,
    long teacherId,
    int year,
    AcademicPeriod period,
    java.math.BigDecimal grade) {}
