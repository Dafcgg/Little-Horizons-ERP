package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.controller.AcademicController;
import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.model.AbsenceSummary;
import com.littlehorizons.erp.service.AcademicService;
import com.littlehorizons.erp.view.ConsoleView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AcademicAbsenceReportTest {
  @Test
  void serviceFiltersAccumulatedAbsentClassesStrictlyAboveThreshold() {
    LocalDate start = LocalDate.of(2026, 2, 2);
    LocalDate end = start.plusDays(6);
    assertEquals(expectedReport(), service(start, end, Clock.systemUTC()).absences(start, end, 3));
  }

  @ParameterizedTest
  @CsvSource({
    "2026-02-02T04:59:59Z, 2026-01-26, 2026-02-01",
    "2026-02-02T05:00:00Z, 2026-02-02, 2026-02-08",
    "2026-02-05T17:00:00Z, 2026-02-02, 2026-02-08",
    "2026-02-09T04:59:59Z, 2026-02-02, 2026-02-08",
    "2026-01-01T17:00:00Z, 2025-12-29, 2026-01-04"
  })
  void currentWeekUsesBogotaMondayThroughSundayAndStrictThreshold(
      String instant, LocalDate start, LocalDate end) {
    Clock clock = Clock.fixed(Instant.parse(instant), ZoneId.of("America/Bogota"));
    assertEquals(expectedReport(), service(start, end, clock).currentWeekAbsences());
  }

  @Test
  void currentWeekMenuRunsWithoutRequestingDatesOrThreshold() {
    Clock clock = Clock.fixed(Instant.parse("2026-02-05T17:00:00Z"), ZoneId.of("America/Bogota"));
    var output = new ByteArrayOutputStream();
    var view =
        new ConsoleView(
            new ByteArrayInputStream("13\n0\n".getBytes(StandardCharsets.UTF_8)),
            new PrintStream(output, true, StandardCharsets.UTF_8));
    new AcademicController(service(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 8), clock), view)
        .run();
    String text = output.toString(StandardCharsets.UTF_8);
    for (String line : expectedReport()) assertTrue(text.contains(line));
    assertFalse(text.contains("Estudiante 2:"));
    assertFalse(text.contains("Desde ("));
    assertFalse(text.contains("Hasta ("));
    assertFalse(text.contains("Más de cuántas"));
  }

  private List<String> expectedReport() {
    return List.of(
        "Estudiante 3: 4 clases ausentes acumuladas", "Estudiante 4: 7 clases ausentes acumuladas");
  }

  private AcademicService service(LocalDate start, LocalDate end, Clock clock) {
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
    return new AcademicService(() -> connection, dao, clock);
  }
}
