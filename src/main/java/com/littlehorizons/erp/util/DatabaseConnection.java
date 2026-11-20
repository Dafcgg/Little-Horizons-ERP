package com.littlehorizons.erp.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Supplies a fresh JDBC connection configured for the local or deployed environment. */
public final class DatabaseConnection implements ConnectionProvider {
  private static final DatabaseConnection INSTANCE = new DatabaseConnection();
  private static final String URL = "ERP_DB_URL";
  private static final String USER = "ERP_DB_USER";
  private static final String PASSWORD = "ERP_DB_PASSWORD";
  private static final Set<String> DATABASE_VARIABLES = Set.of(URL, USER, PASSWORD);

  private DatabaseConnection() {}

  public static DatabaseConnection getInstance() {
    return INSTANCE;
  }

  @Override
  public Connection open() throws SQLException {
    var configuration = configuration(System.getenv(), Path.of(".env"));
    String password = configuration.get(PASSWORD);
    if (password == null)
      throw new BusinessException("Configure ERP_DB_PASSWORD antes de conectar.");
    return DriverManager.getConnection(
        configuration.getOrDefault(
            URL, "jdbc:mysql://localhost:3306/erp_little_horizons?serverTimezone=America/Bogota"),
        configuration.getOrDefault(USER, "little_horizons"),
        password);
  }

  static Map<String, String> configuration(Map<String, String> environment, Path envFile) {
    // Deployments with complete credentials can run without reading the developer's local file.
    if (environment.keySet().containsAll(DATABASE_VARIABLES)) return environment;

    var settings = new HashMap<String, String>();
    try {
      for (String line : Files.readAllLines(envFile)) {
        String[] assignment = line.split("=", 2);
        String key = assignment[0].strip();
        if (assignment.length == 2 && DATABASE_VARIABLES.contains(key)) {
          settings.put(key, unquote(assignment[1].strip()));
        }
      }
    } catch (NoSuchFileException missing) {
      return environment;
    } catch (IOException failure) {
      throw new BusinessException("No se pudo leer la configuración de MySQL en .env.", failure);
    }
    for (String key : DATABASE_VARIABLES) {
      if (environment.containsKey(key)) settings.put(key, environment.get(key));
    }
    return settings;
  }

  private static String unquote(String value) {
    if (value.length() < 2) return value;
    char quote = value.charAt(0);
    if ((quote == '\'' || quote == '"') && value.charAt(value.length() - 1) == quote) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }
}
