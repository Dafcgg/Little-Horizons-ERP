package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.Jdbc;
import com.littlehorizons.erp.dao.jdbc.JdbcResourceDao;
import com.littlehorizons.erp.model.BorrowerType;
import com.littlehorizons.erp.model.SchoolResource;
import com.littlehorizons.erp.service.ResourceService;
import com.littlehorizons.erp.util.ConnectionProvider;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ERP_TEST_DB_URL", matches = ".+")
class ResourceOverdueIntegrationTest {
  private final ConnectionProvider connections =
      () ->
          DriverManager.getConnection(
              System.getenv("ERP_TEST_DB_URL"),
              System.getenv().getOrDefault("ERP_TEST_DB_USER", "root"),
              System.getenv("ERP_TEST_DB_PASSWORD"));
  private final JdbcResourceDao dao = new JdbcResourceDao();
  private final ResourceService service = new ResourceService(connections, dao);

  private long createLoan(BorrowerType type, LocalDate due, String status) throws SQLException {
    long resource =
        service.create(
            new SchoolResource(
                0,
                UUID.randomUUID().toString().substring(0, 12),
                "Overdue test resource",
                1,
                "Test room",
                "Disponible"));
    long loan = service.lend(resource, 1, type, LocalDate.now().plusDays(1));
    String sql =
        switch (type) {
          case STUDENT ->
              """
UPDATE prestamos_estudiante
SET fecha_prestamo=?,fecha_devolucion_esperada=?,estado_prestamo=? WHERE id_prestamo=?
""";
          case TEACHER ->
              """
UPDATE prestamos_profesor
SET fecha_prestamo=?,fecha_devolucion_esperada=?,estado_prestamo=? WHERE id_prestamo=?
""";
        };
    try (Connection connection = connections.open()) {
      Jdbc.update(connection, sql, LocalDate.now().minusDays(5), due, status, loan);
    }
    return loan;
  }

  @Test
  void activePastDueLoansExposeOverdueStatusInEveryReadWithoutChangingStoredStatus()
      throws Exception {
    for (BorrowerType type : BorrowerType.values()) {
      long loan = createLoan(type, LocalDate.now().minusDays(1), "Activo");
      assertEquals("Vencido", service.loan(loan, type).status());
      assertEquals(
          "Vencido",
          service.loans(type).stream()
              .filter(entry -> entry.id() == loan)
              .findFirst()
              .orElseThrow()
              .status());
      try (Connection connection = connections.open()) {
        connection.setAutoCommit(false);
        assertEquals("Vencido", dao.loanEntry(connection, loan, type, true).orElseThrow().status());
        String sql =
            switch (type) {
              case STUDENT ->
                  "SELECT estado_prestamo FROM prestamos_estudiante WHERE id_prestamo=?";
              case TEACHER -> "SELECT estado_prestamo FROM prestamos_profesor WHERE id_prestamo=?";
            };
        assertEquals("Activo", Jdbc.query(connection, sql, row -> row.getString(1), loan).get(0));
        connection.rollback();
      }
    }
  }

  @Test
  void loansDueTodayOrInTheFutureRemainActiveAndAreExcludedFromOverdueReport() throws Exception {
    for (BorrowerType type : BorrowerType.values()) {
      for (LocalDate due : new LocalDate[] {LocalDate.now(), LocalDate.now().plusDays(1)}) {
        long loan = createLoan(type, due, "Activo");
        assertEquals("Activo", service.loan(loan, type).status());
        assertTrue(
            service.overdue().stream()
                .noneMatch(entry -> entry.id() == loan && entry.borrowerType() == type));
      }
    }
  }

  @Test
  void activeAndStoredOverdueLoansCanBeReturnedAndRestoreResourceAvailability() throws Exception {
    for (BorrowerType type : BorrowerType.values()) {
      for (String status : new String[] {"Activo", "Vencido"}) {
        long loan = createLoan(type, LocalDate.now().minusDays(1), status);
        assertTrue(
            service.overdue().stream()
                .anyMatch(entry -> entry.id() == loan && entry.borrowerType() == type));
        long resource = service.loan(loan, type).resourceId();
        service.returnLoan(loan, type);
        assertEquals("Devuelto", service.loan(loan, type).status());
        assertEquals(LocalDate.now(), service.loan(loan, type).returned());
        assertEquals("Disponible", service.resource(resource).status());
        assertTrue(
            service.overdue().stream()
                .noneMatch(entry -> entry.id() == loan && entry.borrowerType() == type));
      }
    }
  }

  @Test
  void cancelledPastDueLoansKeepCancelledStatusAndAreExcludedFromOverdueReport() throws Exception {
    for (BorrowerType type : BorrowerType.values()) {
      long loan = createLoan(type, LocalDate.now().minusDays(1), "Activo");
      service.cancelLoan(loan, type, "Test cancellation");
      assertEquals("Anulado", service.loan(loan, type).status());
      assertTrue(
          service.overdue().stream()
              .noneMatch(entry -> entry.id() == loan && entry.borrowerType() == type));
    }
  }
}
