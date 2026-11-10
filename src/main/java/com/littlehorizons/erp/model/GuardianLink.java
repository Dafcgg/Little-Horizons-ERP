package com.littlehorizons.erp.model;

public record GuardianLink(
    long studentId,
    long guardianId,
    String relationship,
    boolean primary,
    boolean emergency,
    boolean financial) {}
