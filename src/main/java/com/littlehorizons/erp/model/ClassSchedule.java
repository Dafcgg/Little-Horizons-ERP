package com.littlehorizons.erp.model;

public record ClassSchedule(
    long id, long assignmentId, int day, java.time.LocalTime start, java.time.LocalTime end) {}
