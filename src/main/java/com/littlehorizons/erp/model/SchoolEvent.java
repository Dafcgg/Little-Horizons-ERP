package com.littlehorizons.erp.model;

public record SchoolEvent(
    long id,
    String name,
    java.time.LocalDate date,
    String type,
    boolean competitive,
    boolean active) {
  public SchoolEvent(
      long id, String name, java.time.LocalDate date, String type, boolean competitive) {
    this(id, name, date, type, competitive, true);
  }
}
