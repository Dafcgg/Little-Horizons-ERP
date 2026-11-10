package com.littlehorizons.erp.model;

public record TeachingAssignment(
    long id, long teacherId, long subjectId, int year, String section, boolean active) {}
