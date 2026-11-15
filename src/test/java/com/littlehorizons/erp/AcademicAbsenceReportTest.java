package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.model.AbsenceSummary;
import com.littlehorizons.erp.service.AcademicService;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcademicAbsenceReportTest {
  @Test
  void serviceFiltersAccumulatedAbsentClassesStrictlyAboveThreshold() {
    LocalDate start = LocalDate.of(2026, 2, 2);
    LocalDate end = start.plusDays(6);
    Connection connection =
        (Connection)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                  return switch (method.getName()) {
                    case "setTransactionIsolation", "setAutoCommit", "commit", "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                  };
                });
    AcademicDao dao =
        (AcademicDao)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {AcademicDao.class},
                (proxy, method, args) -> {
                  assertEquals("absenceCounts", method.getName());
                  assertSame(connection, args[0]);
                  assertEquals(start, args[1]);
                  assertEquals(end, args[2]);
                  return List.of(
                      new AbsenceSummary(1, 2),
                      new AbsenceSummary(2, 3),
                      new AbsenceSummary(3, 4),
                      new AbsenceSummary(4, 7));
                });
    assertEquals(
        List.of(
            "Estudiante 3: 4 clases ausentes acumuladas",
            "Estudiante 4: 7 clases ausentes acumuladas"),
        new AcademicService(() -> connection, dao).absences(start, end, 3));
  }
}
