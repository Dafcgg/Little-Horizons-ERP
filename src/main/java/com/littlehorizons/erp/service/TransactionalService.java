package com.littlehorizons.erp.service;

import com.littlehorizons.erp.util.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;

public abstract class TransactionalService {
  private final ConnectionProvider connections;

  protected TransactionalService(ConnectionProvider connections) {
    this.connections = connections;
  }

  @FunctionalInterface
  protected interface Work<T> {
    T execute(Connection connection) throws SQLException;
  }

  // The service owns both the connection and the complete transaction boundary.
  protected final <T> T transaction(Work<T> work) {
    try (Connection connection = connections.open()) {
      connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      connection.setAutoCommit(false);
      try {
        T result = work.execute(connection);
        connection.commit();
        return result;
      } catch (SQLException | RuntimeException | Error error) {
        try {
          connection.rollback();
        } catch (SQLException rollbackError) {
          error.addSuppressed(rollbackError);
        }
        if (error instanceof BusinessException rejection) AppLogger.transactionRejected(rejection);
        throw error;
      }
    } catch (SQLException error) {
      AppLogger.error("Database transaction failed", error);
      throw new BusinessException(
          "No se pudo completar la operación. Revise los datos y error_log.txt.", error);
    }
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
    return date != null && date.getYear() >= 1000 && date.getYear() <= 9999;
  }
}
