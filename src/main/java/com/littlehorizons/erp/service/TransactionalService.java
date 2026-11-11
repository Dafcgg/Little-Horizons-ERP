package com.littlehorizons.erp.service;

import com.littlehorizons.erp.util.*;
import java.sql.*;

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
}
