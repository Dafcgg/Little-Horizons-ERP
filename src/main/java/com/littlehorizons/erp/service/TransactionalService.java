package com.littlehorizons.erp.service;

import com.littlehorizons.erp.util.*;
import com.littlehorizons.erp.util.JdbcTransactionManager.Work;

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
}
