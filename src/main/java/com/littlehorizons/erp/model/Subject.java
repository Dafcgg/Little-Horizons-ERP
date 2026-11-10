package com.littlehorizons.erp.model;

public record Subject(long id, String code, String name, long gradeId, boolean active) {
  public Subject(long id, String code, String name, long gradeId) {
    this(id, code, name, gradeId, true);
  }
}
