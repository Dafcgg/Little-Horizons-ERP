package com.littlehorizons.erp.model;

public record ClubMembership(
    long id, long studentId, long clubId, java.time.LocalDate joined, String status) {}
