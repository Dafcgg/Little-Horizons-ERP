package com.littlehorizons.erp.model;

public record Teacher(
    long id,
    String document,
    String firstName,
    String lastName,
    String email,
    String specialization,
    String status) {
  public Teacher(
      long id,
      String document,
      String firstName,
      String lastName,
      String email,
      String specialization) {
    this(id, document, firstName, lastName, email, specialization, "Activo");
  }
}
