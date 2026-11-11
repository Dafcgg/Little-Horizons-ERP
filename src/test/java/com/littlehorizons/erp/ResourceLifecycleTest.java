package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.Jdbc;
import com.littlehorizons.erp.service.TransactionalService;
import com.littlehorizons.erp.util.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResourceLifecycleTest {
  private static final class Lifecycle {
    int commits, rollbacks, closes;
    boolean commitFails, rollbackFails, setupFails;

    Connection connection() {
      return (Connection)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                switch (method.getName()) {
                  case "setAutoCommit":
                  case "setTransactionIsolation":
                    if (setupFails) throw new SQLException("setup failure");
                    return null;
                  case "commit":
                    commits++;
                    if (commitFails) throw new SQLException("commit failure");
                    return null;
                  case "rollback":
                    rollbacks++;
                    if (rollbackFails) throw new SQLException("rollback failure");
                    return null;
                  case "close":
                    closes++;
                    return null;
                  default:
                    throw new UnsupportedOperationException(method.getName());
                }
              });
    }
  }

  private static final class ProbeService extends TransactionalService {
    ProbeService(ConnectionProvider provider) {
      super(provider);
    }

    int run(Throwable failure) {
      return transaction(
          c -> {
            if (failure instanceof SQLException error) throw error;
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
            return 42;
          });
    }
  }

  @Test
  void successCommitsAndCloses() {
    var lifecycle = new Lifecycle();
    assertEquals(42, new ProbeService(lifecycle::connection).run(null));
    assertEquals(1, lifecycle.commits);
    assertEquals(0, lifecycle.rollbacks);
    assertEquals(1, lifecycle.closes);
  }

  @Test
  void sqlFailureRollsBackAndCloses() {
    var lifecycle = new Lifecycle();
    assertThrows(
        BusinessException.class,
        () -> new ProbeService(lifecycle::connection).run(new SQLException("failure")));
    assertEquals(0, lifecycle.commits);
    assertEquals(1, lifecycle.rollbacks);
    assertEquals(1, lifecycle.closes);
  }

  @Test
  void runtimeFailureRollsBackAndPreservesOriginalException() {
    var lifecycle = new Lifecycle();
    var expected = new BusinessException("failure");
    assertSame(
        expected,
        assertThrows(
            BusinessException.class, () -> new ProbeService(lifecycle::connection).run(expected)));
    assertEquals(1, lifecycle.rollbacks);
    assertEquals(1, lifecycle.closes);
  }

  @Test
  void commitFailureAttemptsRollbackAndCloses() {
    var lifecycle = new Lifecycle();
    lifecycle.commitFails = true;
    assertThrows(BusinessException.class, () -> new ProbeService(lifecycle::connection).run(null));
    assertEquals(1, lifecycle.commits);
    assertEquals(1, lifecycle.rollbacks);
    assertEquals(1, lifecycle.closes);
  }

  @Test
  void rollbackFailureIsSuppressedAndConnectionStillCloses() {
    var lifecycle = new Lifecycle();
    lifecycle.rollbackFails = true;
    var expected = new BusinessException("original");
    assertSame(
        expected,
        assertThrows(
            BusinessException.class, () -> new ProbeService(lifecycle::connection).run(expected)));
    assertEquals("rollback failure", expected.getSuppressed()[0].getMessage());
    assertEquals(1, lifecycle.closes);
  }

  @Test
  void setupFailureClosesConnection() {
    var lifecycle = new Lifecycle();
    lifecycle.setupFails = true;
    assertThrows(BusinessException.class, () -> new ProbeService(lifecycle::connection).run(null));
    assertEquals(1, lifecycle.closes);
  }

  @Test
  void errorAlsoTriggersRollbackAndClose() {
    var lifecycle = new Lifecycle();
    var expected = new AssertionError("failure");
    assertSame(
        expected,
        assertThrows(
            AssertionError.class, () -> new ProbeService(lifecycle::connection).run(expected)));
    assertEquals(1, lifecycle.rollbacks);
    assertEquals(1, lifecycle.closes);
  }

  @Test
  void mapperFailureClosesStatementAndResultSet() throws Exception {
    AtomicInteger statementCloses = new AtomicInteger(), resultCloses = new AtomicInteger();
    ResultSet result =
        (ResultSet)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {ResultSet.class},
                (p, m, a) ->
                    switch (m.getName()) {
                      case "next" -> true;
                      case "close" -> {
                        resultCloses.incrementAndGet();
                        yield null;
                      }
                      default -> throw new UnsupportedOperationException(m.getName());
                    });
    PreparedStatement statement =
        (PreparedStatement)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class},
                (p, m, a) ->
                    switch (m.getName()) {
                      case "executeQuery" -> result;
                      case "close" -> {
                        statementCloses.incrementAndGet();
                        yield null;
                      }
                      default -> throw new UnsupportedOperationException(m.getName());
                    });
    Connection connection =
        (Connection)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Connection.class},
                (p, m, a) -> {
                  if (m.getName().equals("prepareStatement")) return statement;
                  throw new UnsupportedOperationException(m.getName());
                });
    assertThrows(
        SQLException.class,
        () ->
            Jdbc.query(
                connection,
                "SELECT 1",
                row -> {
                  throw new SQLException("mapping failure");
                }));
    assertEquals(1, statementCloses.get());
    assertEquals(1, resultCloses.get());
  }
}
