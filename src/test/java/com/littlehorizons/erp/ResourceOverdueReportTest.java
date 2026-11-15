package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.ResourceDao;
import com.littlehorizons.erp.model.BorrowerType;
import com.littlehorizons.erp.model.Loan;
import com.littlehorizons.erp.model.LoanEntry;
import com.littlehorizons.erp.service.ResourceService;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceOverdueReportTest {
  @Test
  void reportFiltersOpenPastDueLoansFromBothBorrowerTypesAndPreservesTheirIdentity() {
    LocalDate today = LocalDate.now();
    List<LoanEntry> studentLoans =
        List.of(
            entry(1, BorrowerType.STUDENT, today.minusDays(1), null, "Activo"),
            entry(2, BorrowerType.STUDENT, today, null, "Activo"),
            entry(3, BorrowerType.STUDENT, today.plusDays(1), null, "Activo"),
            entry(4, BorrowerType.STUDENT, today.minusDays(1), today, "Devuelto"),
            entry(5, BorrowerType.STUDENT, today.minusDays(1), today, "Anulado"));
    List<LoanEntry> teacherLoans =
        List.of(entry(1, BorrowerType.TEACHER, today.minusDays(2), null, "Vencido"));
    ResourceDao dao =
        (ResourceDao)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {ResourceDao.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("loans"))
                    return arguments[1] == BorrowerType.STUDENT ? studentLoans : teacherLoans;
                  throw new UnsupportedOperationException(method.getName());
                });
    Connection connection =
        (Connection)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) ->
                    switch (method.getName()) {
                      case "setAutoCommit", "setTransactionIsolation", "commit", "close" -> null;
                      default -> throw new UnsupportedOperationException(method.getName());
                    });

    assertEquals(
        List.of(
            new Loan(1, 101, 201, BorrowerType.STUDENT, today.minusDays(1)),
            new Loan(1, 101, 201, BorrowerType.TEACHER, today.minusDays(2))),
        new ResourceService(() -> connection, dao).overdue());
  }

  private LoanEntry entry(
      long id, BorrowerType type, LocalDate due, LocalDate returned, String status) {
    return new LoanEntry(id, 100 + id, 200 + id, type, due.minusDays(1), due, returned, status);
  }
}
