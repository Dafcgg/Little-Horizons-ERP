package com.littlehorizons.erp.model;

public record AttendanceRecord(
    long id,
    long studentId,
    long subjectId,
    long teacherId,
    int year,
    AcademicPeriod period,
    java.time.LocalDate date,
    AttendanceStatus status) {}
