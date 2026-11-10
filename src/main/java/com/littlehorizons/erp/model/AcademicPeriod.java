package com.littlehorizons.erp.model;

public enum AcademicPeriod {
  I("I"),
  II("II"),
  III("III"),
  IV("IV"),
  FINAL("Final");
  private final String databaseValue;

  AcademicPeriod(String databaseValue) {
    this.databaseValue = databaseValue;
  }

  public String databaseValue() {
    return databaseValue;
  }
}
