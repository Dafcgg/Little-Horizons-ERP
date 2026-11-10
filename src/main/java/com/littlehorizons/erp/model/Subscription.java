package com.littlehorizons.erp.model;

public record Subscription(
    long id,
    long studentId,
    long subjectId,
    int year,
    AcademicPeriod period,
    String section,
    String status) {}
