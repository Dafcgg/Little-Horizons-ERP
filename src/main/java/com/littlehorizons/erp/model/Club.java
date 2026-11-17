package com.littlehorizons.erp.model;

public record Club(
    long id, String name, long teacherId, int capacity, boolean active, String description) {
  public Club(long id, String name, long teacherId, int capacity, boolean active) {
    this(id, name, teacherId, capacity, active, null);
  }

  public Club(long id, String name, long teacherId, int capacity) {
    this(id, name, teacherId, capacity, true);
  }
}
