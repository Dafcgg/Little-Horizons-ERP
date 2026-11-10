package com.littlehorizons.erp.model;

import java.math.BigDecimal;

public record FeeQuote(BigDecimal amount, BigDecimal taxRate) {}
