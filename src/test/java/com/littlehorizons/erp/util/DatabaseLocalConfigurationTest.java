package com.littlehorizons.erp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseLocalConfigurationTest {
  @TempDir Path directory;

  @Test
  void readsDatabaseSettingsWhenTheLauncherSuppliesNoEnvironment() throws Exception {
    Path envFile = directory.resolve(".env");
    Files.writeString(
        envFile,
        """
        # Local JDBC settings
        ERP_DB_URL='jdbc:mysql://localhost:3307/erp?serverTimezone=America/Bogota'
        ERP_DB_USER=root
        ERP_DB_PASSWORD=" value=#kept "
        ERP_INVOICE_DIR=invoices
        """);

    assertEquals(
        Map.of(
            "ERP_DB_URL", "jdbc:mysql://localhost:3307/erp?serverTimezone=America/Bogota",
            "ERP_DB_USER", "root",
            "ERP_DB_PASSWORD", " value=#kept "),
        DatabaseConnection.configuration(Map.of(), envFile));
  }

  @Test
  void keepsExplicitEnvironmentValuesAndFillsInMissingSettings() throws Exception {
    Path envFile = directory.resolve(".env");
    Files.writeString(envFile, "ERP_DB_USER=local-user\nERP_DB_PASSWORD=local-password\n");

    assertEquals(
        Map.of("ERP_DB_USER", "local-user", "ERP_DB_PASSWORD", "process-password"),
        DatabaseConnection.configuration(Map.of("ERP_DB_PASSWORD", "process-password"), envFile));
  }

  @Test
  void retainsTheEnvironmentWhenTheLocalFileIsAbsent() {
    var environment = Map.of("ERP_DB_PASSWORD", "process-password");

    assertEquals(environment, DatabaseConnection.configuration(environment, directory.resolve(".env")));
  }
}
