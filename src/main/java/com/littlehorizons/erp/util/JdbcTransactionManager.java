package com.littlehorizons.erp.util;

import java.sql.Connection;
import java.sql.SQLException;

/** Owns one JDBC connection and its commit or rollback for each service operation. */
public final class JdbcTransactionManager {
  private final ConnectionProvider connections;

  public JdbcTransactionManager(ConnectionProvider connections) {
    this.connections = connections;
  }

  @FunctionalInterface
  public interface Work<T> {
    T execute(Connection connection) throws SQLException;
  }

  // Nested work must reuse this connection to preserve a single transaction boundary.
  public <T> T execute(Work<T> work) {
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
          // Keep the primary failure as the cause seen by callers.
          error.addSuppressed(rollbackError);
          AppLogger.warning("Database rollback could not be completed", rollbackError);
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
}
