package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.unique;

import com.littlehorizons.erp.dao.jdbc.Jdbc;
import com.littlehorizons.erp.dao.jdbc.JdbcStudentDao;
import com.littlehorizons.erp.model.Student;
import com.littlehorizons.erp.service.StudentService;
import com.littlehorizons.erp.util.AppLogger;
import com.littlehorizons.erp.util.BusinessException;
import com.littlehorizons.erp.util.ConnectionProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

/** Shared setup for the seeded MySQL integration suites. */
abstract class DatabaseIntegrationFixture {
  final ConnectionProvider connections = MysqlFixture.CONNECTIONS;
  @TempDir Path directory;

  long count(String sql, Object... values) throws SQLException {
    try (Connection c = connections.open()) {
      return Jdbc.count(c, sql, values);
    }
  }

  long student() {
    return new StudentService(connections, new JdbcStudentDao())
        .register(
            new Student(
                0, unique(), "Test", "Student", LocalDate.of(2015, 1, 1), "Test street", "Activo"),
            1,
            "Tutor");
  }

  @BeforeAll
  static void logging() throws IOException {
    AppLogger.configure();
  }

  List<Boolean> race(Runnable first, Runnable second) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (Runnable task : List.of(first, second))
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    task.run();
                    return true;
                  } catch (BusinessException expected) {
                    return false;
                  }
                }));
      start.countDown();
      List<Boolean> results = new ArrayList<>();
      for (Future<Boolean> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
      return results;
    } finally {
      executor.shutdownNow();
    }
  }
}
