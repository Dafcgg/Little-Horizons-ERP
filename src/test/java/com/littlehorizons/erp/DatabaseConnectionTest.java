package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.util.DatabaseConnection;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class DatabaseConnectionTest {
  @Test
  void concurrentCallersShareOnePrivatelyConstructedProvider() throws Exception {
    assertTrue(
        Modifier.isPrivate(DatabaseConnection.class.getDeclaredConstructor().getModifiers()));
    var executor = Executors.newFixedThreadPool(8);
    try {
      var futures = new ArrayList<Future<DatabaseConnection>>();
      for (int index = 0; index < 64; index++)
        futures.add(executor.submit(DatabaseConnection::getInstance));
      for (var future : futures)
        assertSame(DatabaseConnection.getInstance(), future.get(5, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "ERP_DB_URL", matches = ".+")
  void singletonOpensIndependentTransactionalConnections() throws Exception {
    var provider = DatabaseConnection.getInstance();
    try (Connection first = provider.open();
        Connection second = provider.open()) {
      assertNotSame(first, second);
      first.setAutoCommit(false);
      assertTrue(second.getAutoCommit());
      first.close();
      assertTrue(second.isValid(2));
    }
  }
}
