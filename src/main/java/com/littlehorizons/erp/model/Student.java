package com.littlehorizons.erp.model;

public record Student(
    long id,
    String document,
    String firstName,
    String lastName,
    java.time.LocalDate birthDate,
    String address,
    String status) {}
