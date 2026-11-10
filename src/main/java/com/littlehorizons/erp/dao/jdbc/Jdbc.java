package com.littlehorizons.erp.dao.jdbc;

import java.sql.*;
import java.util.*;

public final class Jdbc {
  private Jdbc() {}

  @FunctionalInterface
  public interface Mapper<T> {
    T map(ResultSet result) throws SQLException;
  }

  private static void bind(PreparedStatement statement, Object... values) throws SQLException {
    for (int index = 0; index < values.length; index++)
      statement.setObject(index + 1, values[index]);
  }

  public static int update(Connection connection, String sql, Object... values)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, values);
      return statement.executeUpdate();
    }
  }

  public static long insert(Connection connection, String sql, Object... values)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      bind(statement, values);
      statement.executeUpdate();
      try (ResultSet keys = statement.getGeneratedKeys()) {
        if (!keys.next()) throw new SQLException("No generated key returned");
        return keys.getLong(1);
      }
    }
  }

  public static <T> List<T> query(
      Connection connection, String sql, Mapper<T> mapper, Object... values) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, values);
      try (ResultSet result = statement.executeQuery()) {
        List<T> rows = new ArrayList<>();
        while (result.next()) rows.add(mapper.map(result));
        return rows;
      }
    }
  }

  public static long count(Connection connection, String sql, Object... values)
      throws SQLException {
    return query(connection, sql, result -> result.getLong(1), values).get(0);
  }
}
