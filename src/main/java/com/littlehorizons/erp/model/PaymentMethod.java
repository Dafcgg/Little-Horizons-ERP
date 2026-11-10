package com.littlehorizons.erp.model;

public enum PaymentMethod {
  CASH("Efectivo"),
  TRANSFER("Transferencia"),
  DEBIT_CARD("Tarjeta Débito"),
  CREDIT_CARD("Tarjeta Crédito"),
  CHEQUE("Cheque");
  private final String databaseValue;

  PaymentMethod(String databaseValue) {
    this.databaseValue = databaseValue;
  }

  public String databaseValue() {
    return databaseValue;
  }
}
