package com.littlehorizons.erp.model;

public record InvoiceLine(
    String description,
    int quantity,
    java.math.BigDecimal unitPrice,
    java.math.BigDecimal subtotal,
    java.math.BigDecimal tax) {}
