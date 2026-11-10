package com.littlehorizons.erp.model;

public enum BorrowerType {
  STUDENT("prestamos_estudiante"),
  TEACHER("prestamos_profesor");
  private final String databaseValue;

  BorrowerType(String databaseValue) {
    this.databaseValue = databaseValue;
  }

  public String databaseValue() {
    return databaseValue;
  }
}
