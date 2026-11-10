package com.littlehorizons.erp.model;

public record Enrollment(long id, long studentId, long gradeId, int year, String status) {}
