package com.littlehorizons.erp.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection implements ConnectionProvider {
  public Connection open() throws SQLException {
    String password = System.getenv("ERP_DB_PASSWORD");
    if (password == null)
      throw new BusinessException("Configure ERP_DB_PASSWORD antes de conectar.");
    return DriverManager.getConnection(
        System.getenv()
            .getOrDefault(
                "ERP_DB_URL",
                "jdbc:mysql://localhost:3306/erp_little_horizons?serverTimezone=America/Bogota"),
        System.getenv().getOrDefault("ERP_DB_USER", "little_horizons"),
        password);
  }
}
