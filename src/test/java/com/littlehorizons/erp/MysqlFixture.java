package com.littlehorizons.erp;

import com.littlehorizons.erp.util.ConnectionProvider;
import java.sql.DriverManager;
import java.util.UUID;

final class MysqlFixture {
  // The integration launcher creates a seeded MySQL instance and removes it on exit.
  static final String URL = "ERP_TEST_DB_URL";
  static final ConnectionProvider CONNECTIONS =
      () ->
          DriverManager.getConnection(
              System.getenv(URL),
              System.getenv().getOrDefault("ERP_TEST_DB_USER", "root"),
              System.getenv("ERP_TEST_DB_PASSWORD"));

  private MysqlFixture() {}

  static String unique() {
    return UUID.randomUUID().toString().substring(0, 12);
  }
}
