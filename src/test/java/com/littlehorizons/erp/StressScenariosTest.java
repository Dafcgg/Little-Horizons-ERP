package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "ERP_TEST_DB_URL", matches = ".+")
class StressScenariosTest {
  private static final int ROUNDS = 5;
  private static final int CONTENDERS = 20;
  private final ConnectionProvider connections =
      () ->
          DriverManager.getConnection(
              System.getenv("ERP_TEST_DB_URL"),
              System.getenv().getOrDefault("ERP_TEST_DB_USER", "root"),
              System.getenv("ERP_TEST_DB_PASSWORD"));
  @TempDir Path directory;

  @BeforeAll
  static void logging() throws Exception {
    AppLogger.configure();
  }

  @Test
  void twentyConcurrentStudentsNeverExceedThreeSeatsAcrossFiveRounds() throws Exception {
    var service = new ActivityService(connections, new JdbcActivityDao());
    int accepted = 0;
    for (int round = 0; round < ROUNDS; round++) {
      long club = service.createClub(new Club(0, "Stress " + UUID.randomUUID(), 1, 3));
      List<Runnable> tasks = new ArrayList<>();
      for (int index = 1; index <= CONTENDERS; index++) {
        long student = index;
        tasks.add(() -> service.join(club, student));
      }
      int confirmed = compete(tasks, "El club no tiene cupos.");
      assertEquals(3, confirmed, "round " + round);
      accepted += confirmed;
      try (Connection c = connections.open()) {
        assertEquals(
            3,
            Jdbc.count(
                c,
                "SELECT COUNT(*) FROM membresias_club WHERE id_club=? AND"
                    + " estado_membresia='Activa'",
                club));
      }
    }
    assertEquals(15, accepted);
    System.out.println(
        "QA_CLUB: attempts=100 accepted=15 expected_rejections=85 rounds=5 capacity=3");
  }

  @Test
  void twentyMixedBorrowersProduceExactlyOneLoanAcrossBothTables() throws Exception {
    var service = new ResourceService(connections, new JdbcResourceDao());
    int accepted = 0;
    for (int round = 0; round < ROUNDS; round++) {
      long resource =
          service.create(
              new SchoolResource(
                  0, UUID.randomUUID().toString(), "Stress resource", 1, "QA room", "Disponible"));
      List<Runnable> tasks = new ArrayList<>();
      for (int index = 1; index <= CONTENDERS; index++) {
        long borrower = index;
        BorrowerType type = index % 2 == 0 ? BorrowerType.STUDENT : BorrowerType.TEACHER;
        tasks.add(() -> service.lend(resource, borrower, type, LocalDate.now().plusDays(7)));
      }
      int confirmed = compete(tasks, "El recurso no está disponible.");
      assertEquals(1, confirmed, "round " + round);
      accepted += confirmed;
      try (Connection c = connections.open()) {
        long studentLoans =
            Jdbc.count(
                c,
                "SELECT COUNT(*) FROM prestamos_estudiante WHERE id_recurso=? AND"
                    + " fecha_devolucion_real IS NULL",
                resource);
        long teacherLoans =
            Jdbc.count(
                c,
                "SELECT COUNT(*) FROM prestamos_profesor WHERE id_recurso=? AND"
                    + " fecha_devolucion_real IS NULL",
                resource);
        assertEquals(1, studentLoans + teacherLoans);
        assertEquals(
            "Prestado",
            Jdbc.query(
                    c,
                    "SELECT estado_recurso FROM recursos WHERE id_recurso=?",
                    r -> r.getString(1),
                    resource)
                .get(0));
      }
    }
    assertEquals(5, accepted);
    System.out.println("QA_LOAN: attempts=100 accepted=5 expected_rejections=95 rounds=5 tables=2");
  }

  @Test
  void gradeRangeIsCheckedInJavaAndMysqlWhileRawSqlPrecisionIsRounded() throws Exception {
    for (String value : List.of("5.5", "-0.1", "3.45")) {
      assertThrows(
          BusinessException.class, () -> AcademicService.validateGrade(new BigDecimal(value)));
    }
    AcademicService.validateGrade(new BigDecimal("0.0"));
    AcademicService.validateGrade(new BigDecimal("5.0"));
    try (Connection c = connections.open()) {
      c.setAutoCommit(false);
      try {
        BigDecimal before =
            Jdbc.query(
                    c,
                    "SELECT valor_nota FROM registros_academicos WHERE id_registro=1",
                    r -> r.getBigDecimal(1))
                .get(0);
        assertThrows(
            SQLException.class,
            () ->
                Jdbc.update(
                    c,
                    "UPDATE registros_academicos SET valor_nota=? WHERE id_registro=1",
                    new BigDecimal("5.5")));
        assertEquals(
            before,
            Jdbc.query(
                    c,
                    "SELECT valor_nota FROM registros_academicos WHERE id_registro=1",
                    r -> r.getBigDecimal(1))
                .get(0));
        Jdbc.update(
            c,
            "UPDATE registros_academicos SET valor_nota=? WHERE id_registro=1",
            new BigDecimal("3.45"));
        assertEquals(
            new BigDecimal("3.5"),
            Jdbc.query(
                    c,
                    "SELECT valor_nota FROM registros_academicos WHERE id_registro=1",
                    r -> r.getBigDecimal(1))
                .get(0));
      } finally {
        c.rollback();
      }
    }
    System.out.println(
        "QA_GRADE: Java rejects 5.5/-0.1/3.45; MySQL CHECK rejects 5.5; direct SQL 3.45 rounds to"
            + " 3.5 (rolled back)");
  }

  @Test
  void realFileWriteFailureRetainsPaymentNumberAndCufeForRetry() throws Exception {
    var dao = new JdbcFinanceDao();
    Path invoices = directory.resolve("invoices");
    var renderer = new PdfInvoiceGenerator(invoices);
    InvoiceRenderer blocked =
        invoice -> {
          Path destination = invoices.resolve("factura_%06d.pdf".formatted(invoice.id()));
          Files.createDirectories(destination);
          Files.writeString(
              destination.resolve("blocker.txt"), "Simulated non-replaceable destination");
          return renderer.render(invoice);
        };
    var failing =
        new FinanceService(connections, dao, blocked, SchoolInformation.fromEnvironment());
    String reference = UUID.randomUUID().toString();
    long obligation = failing.createObligation(1, 1, LocalDate.now(), reference);
    var request =
        new PaymentRequest(
            obligation,
            1,
            new BigDecimal("100.00"),
            LocalDate.now(),
            PaymentMethod.CASH,
            reference);
    PaymentResult first = failing.confirmPayment(request);
    assertFalse(first.pdfGenerated());
    Invoice snapshot;
    try (Connection c = connections.open()) {
      snapshot = dao.invoice(c, first.invoiceId()).orElseThrow();
      assertEquals("Fallida", snapshot.status());
      assertEquals(1, Jdbc.count(c, "SELECT COUNT(*) FROM pagos WHERE numero_recibo=?", reference));
      assertEquals(
          new BigDecimal("100.00"),
          Jdbc.query(
                  c,
                  "SELECT monto_pagado FROM pagos WHERE id_pago=?",
                  r -> r.getBigDecimal(1),
                  first.paymentId())
              .get(0));
    }
    Path blockedPath = invoices.resolve("factura_%06d.pdf".formatted(first.invoiceId()));
    Files.delete(blockedPath.resolve("blocker.txt"));
    Files.delete(blockedPath);
    var working =
        new FinanceService(connections, dao, renderer, SchoolInformation.fromEnvironment());
    PaymentResult retried = working.confirmPayment(request);
    assertTrue(retried.pdfGenerated());
    assertEquals(first.paymentId(), retried.paymentId());
    assertEquals(first.invoiceId(), retried.invoiceId());
    try (Connection c = connections.open()) {
      Invoice recovered = dao.invoice(c, first.invoiceId()).orElseThrow();
      assertEquals(snapshot.number(), recovered.number());
      assertEquals(snapshot.cufe(), recovered.cufe());
      assertEquals("Generada", recovered.status());
      assertTrue(Files.isRegularFile(Path.of(recovered.path())));
      assertEquals(1, Jdbc.count(c, "SELECT COUNT(*) FROM pagos WHERE numero_recibo=?", reference));
      assertEquals(
          1, Jdbc.count(c, "SELECT COUNT(*) FROM facturas WHERE id_pago=?", first.paymentId()));
    }
    try (var files = Files.list(invoices)) {
      assertEquals(1, files.count());
    }
    System.out.println(
        "QA_DISK: real filesystem failure; payment/invoice/number/CUFE preserved; retry creates one"
            + " PDF with no extra payment");
  }

  private int compete(List<Runnable> tasks, String expectedMessage) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch ready = new CountDownLatch(tasks.size()), start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (Runnable task : tasks)
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(30, TimeUnit.SECONDS))
                    throw new AssertionError("Start barrier timeout");
                  try {
                    task.run();
                    return true;
                  } catch (BusinessException rejection) {
                    if (!expectedMessage.equals(rejection.getMessage()))
                      throw new AssertionError("Unexpected rejection", rejection);
                    return false;
                  }
                }));
      assertTrue(ready.await(30, TimeUnit.SECONDS), "Not all contenders reached the start barrier");
      start.countDown();
      int accepted = 0;
      for (Future<Boolean> future : futures) if (future.get(45, TimeUnit.SECONDS)) accepted++;
      return accepted;
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Worker threads did not stop");
    }
  }
}
