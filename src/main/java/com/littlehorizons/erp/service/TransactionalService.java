package com.littlehorizons.erp.service;

import com.littlehorizons.erp.util.*;
import com.littlehorizons.erp.util.JdbcTransactionManager.Work;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Shares transaction execution and input rules across application services. */
public abstract class TransactionalService {
  private final JdbcTransactionManager transactions;

  protected TransactionalService(ConnectionProvider connections) {
    this.transactions = new JdbcTransactionManager(connections);
  }

  protected final <T> T transaction(Work<T> work) {
    return transactions.execute(work);
  }

  protected static void require(boolean condition, String message) {
    if (!condition) throw new BusinessException(message);
  }

  protected static String text(String value, String field) {
    require(value != null && !value.isBlank(), field + " es obligatorio.");
    return value.trim();
  }

  protected static String text(String value, String field, int limit) {
    String trimmed = text(value, field);
    textLength(value, field, limit);
    return trimmed;
  }

  protected static void textLength(String value, String field, int limit) {
    // A supplementary Unicode character uses two Java code units but one VARCHAR character.
    require(
        value == null || value.codePointCount(0, value.length()) <= limit,
        field + " admite máximo " + limit + " caracteres.");
  }

  protected static void textByteLength(String value, String field, int limit) {
    require(
        value == null || value.getBytes(StandardCharsets.UTF_8).length <= limit,
        field + " admite máximo " + limit + " bytes en UTF-8.");
  }

  protected static boolean isDatabaseDate(LocalDate date) {
    // LocalDate supports values outside MySQL DATE's supported range.
    return date != null && date.getYear() >= 1000 && date.getYear() <= 9999;
  }
}
