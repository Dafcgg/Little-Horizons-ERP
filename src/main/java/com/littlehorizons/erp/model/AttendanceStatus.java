package com.littlehorizons.erp.model;

public enum AttendanceStatus {
  PRESENT("Presente"),
  ABSENT("Ausente"),
  LATE("Tardanza"),
  MEDICAL_EXCUSE("Excusa Médica");
  private final String databaseValue;

  AttendanceStatus(String databaseValue) {
    this.databaseValue = databaseValue;
  }

  public String databaseValue() {
    return databaseValue;
  }
}
