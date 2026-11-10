package com.littlehorizons.erp.model;

public record Guardian(
    long id,
    String document,
    String firstName,
    String lastName,
    String phone,
    String address,
    boolean active) {
  public Guardian(
      long id, String document, String firstName, String lastName, String phone, String address) {
    this(id, document, firstName, lastName, phone, address, true);
  }
}
