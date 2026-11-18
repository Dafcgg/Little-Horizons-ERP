package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.FinanceDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.FinanceService;
import com.littlehorizons.erp.util.*;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import org.junit.jupiter.api.Test;

class FinanceFileStatusTest {
  private Connection connection() {
    return (Connection)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, arguments) -> {
              return switch (method.getName()) {
                case "setTransactionIsolation", "setAutoCommit", "commit", "rollback", "close" ->
                    null;
                default -> throw new UnsupportedOperationException(method.getName());
              };
            });
  }

  private Invoice invoice() {
    return new Invoice(
        7,
        3,
        "LH-000007",
        LocalDate.now(),
        LocalTime.NOON,
        "Test customer",
        "123",
        "Test address",
        "Test school",
        "900000000",
        "Test school address",
        "0000000000",
        "test@example.invalid",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.TEN,
        "test-cufe",
        "Generada",
        "previous.txt",
        List.of());
  }

  @Test
  void missingFailureStatusUpdateRaisesControlledExceptionAndIsLogged() {
    FinanceDao dao =
        (FinanceDao)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {FinanceDao.class},
                (proxy, method, arguments) ->
                    switch (method.getName()) {
                      case "invoice" -> Optional.of(invoice());
                      case "fileStatus" -> 0;
                      default -> throw new UnsupportedOperationException(method.getName());
                    });
    var service =
        new FinanceService(
            this::connection,
            dao,
            invoice -> {
              throw new IOException("Output file is locked");
            },
            SchoolInformation.fromEnvironment());
    List<LogRecord> records = new ArrayList<>();
    Handler handler =
        new Handler() {
          public void publish(LogRecord record) {
            records.add(record);
          }

          public void flush() {}

          public void close() {}
        };
    Logger logger = Logger.getLogger("com.littlehorizons.erp");
    logger.addHandler(handler);
    try {
      BusinessException error = assertThrows(BusinessException.class, () -> service.regenerate(7));
      assertTrue(error.getMessage().contains("No registre otro pago."));
      assertInstanceOf(BusinessException.class, error.getCause());
      assertEquals("No se actualizó el estado de la factura 7.", error.getCause().getMessage());
      assertTrue(records.stream().anyMatch(record -> record.getThrown() == error.getCause()));
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void rejectedGeneratedStatusDoesNotReportFileSuccess() {
    AtomicInteger failureUpdates = new AtomicInteger();
    FinanceDao dao =
        (FinanceDao)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {FinanceDao.class},
                (proxy, method, arguments) ->
                    switch (method.getName()) {
                      case "invoice" -> Optional.of(invoice());
                      case "markFileGenerated" -> false;
                      case "fileStatus" -> {
                        assertEquals("Fallida", arguments[2]);
                        yield failureUpdates.incrementAndGet();
                      }
                      default -> throw new UnsupportedOperationException(method.getName());
                    });
    var service =
        new FinanceService(
            this::connection,
            dao,
            invoice -> Path.of("generated.txt"),
            SchoolInformation.fromEnvironment());

    assertFalse(service.regenerate(7).fileGenerated());
    assertEquals(1, failureUpdates.get());
  }

  @Test
  void overdueReportRequiresPositiveBalanceOlderThanOneMonth() {
    LocalDate cutoff = LocalDate.now().minusMonths(1);
    Balance overdue = new Balance(1, 1, "Tuition", cutoff.minusDays(1), BigDecimal.TEN);
    List<Balance> balances =
        List.of(
            overdue,
            new Balance(2, 1, "Tuition", cutoff, BigDecimal.TEN),
            new Balance(3, 1, "Tuition", cutoff.plusDays(1), BigDecimal.TEN),
            new Balance(4, 1, "Tuition", cutoff.minusDays(1), BigDecimal.ZERO),
            new Balance(5, 1, "Tuition", cutoff.minusDays(1), BigDecimal.ONE.negate()));
    FinanceDao dao =
        (FinanceDao)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {FinanceDao.class},
                (proxy, method, arguments) -> {
                  assertEquals("balances", method.getName());
                  assertEquals(cutoff, arguments[1]);
                  return balances;
                });
    var service =
        new FinanceService(
            this::connection,
            dao,
            invoice -> Path.of("unused.txt"),
            SchoolInformation.fromEnvironment());

    assertEquals(List.of(overdue), service.overdue());
  }
}
