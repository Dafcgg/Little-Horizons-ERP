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

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class StressScenariosTest {
  private final ConnectionProvider connections = MysqlFixture.CONNECTIONS;
  @TempDir Path directory;

  @BeforeAll
  static void logging() throws Exception {
    AppLogger.configure();
  }

  @RepeatedTest(5)
  void fortyStudentsCannotTakeMoreThanTwoClubSeats() throws Exception {
    var activity = new ActivityService(connections, new JdbcActivityDao());
    var students = new StudentService(connections, new JdbcStudentDao());
    long club = activity.createClub(new Club(0, "QA " + MysqlFixture.unique(), 1, 2));
    List<Runnable> tasks = new ArrayList<>();
    for (int index = 0; index < 40; index++) {
      long student =
          students.register(
              new Student(
                  0,
                  MysqlFixture.unique(),
                  "Club",
                  "Contender",
                  LocalDate.of(2012, 2, 29),
                  null,
                  "Activo"),
              1,
              "Tutor");
      tasks.add(() -> activity.join(club, student));
    }
    assertEquals(2, compete(tasks, "El club no tiene cupos."));
    try (Connection c = connections.open()) {
      assertEquals(
          2,
          Jdbc.count(
              c,
              "SELECT COUNT(*) FROM membresias_club WHERE id_club=? AND estado_membresia='Activa'",
              club));
    }
  }

  @RepeatedTest(5)
  void thirtyMixedBorrowersProduceExactlyOneLoanAcrossBothTables() throws Exception {
    var service = new ResourceService(connections, new JdbcResourceDao());
    long resource =
        service.create(
            new SchoolResource(
                0, MysqlFixture.unique(), "Stress resource", 1, "QA room", "Disponible"));
    List<Runnable> tasks = new ArrayList<>();
    for (int index = 0; index < 30; index++) {
      long borrower = index / 2 + 1;
      BorrowerType type = index % 2 == 0 ? BorrowerType.STUDENT : BorrowerType.TEACHER;
      tasks.add(() -> service.lend(resource, borrower, type, LocalDate.now().plusDays(7)));
    }
    assertEquals(1, compete(tasks, "El recurso no está disponible."));
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
              "SELECT COUNT(*) FROM prestamos_profesor WHERE id_recurso=? AND fecha_devolucion_real"
                  + " IS NULL",
              resource);
      assertEquals(1, studentLoans + teacherLoans);
    }
    assertEquals("Prestado", service.resource(resource).status());
  }

  @RepeatedTest(5)
  void twentyFivePaymentsSettleTheBalanceOnce() throws Exception {
    var dao = new JdbcFinanceDao();
    var service = finance(dao);
    BigDecimal total = new BigDecimal("47.20");
    long obligation = obligation(service, total);
    List<Runnable> tasks = new ArrayList<>();
    var winningPayments = new ConcurrentLinkedQueue<PaymentResult>();
    for (int index = 0; index < 25; index++) {
      String receipt = MysqlFixture.unique();
      tasks.add(
          () -> winningPayments.add(service.confirmPayment(request(obligation, total, receipt))));
    }
    assertEquals(1, compete(tasks, "El pago supera el saldo pendiente."));
    PaymentResult winner = winningPayments.element();
    assertTrue(winner.pdfGenerated());
    try (Connection c = connections.open()) {
      var debt = dao.lockObligation(c, obligation).orElseThrow();
      assertEquals(new BigDecimal("0.00"), debt.amount().subtract(debt.paid()));
      assertEquals(
          1, Jdbc.count(c, "SELECT COUNT(*) FROM pagos WHERE id_obligacion=?", obligation));
      assertEquals(
          1, Jdbc.count(c, "SELECT COUNT(*) FROM facturas WHERE id_pago=?", winner.paymentId()));
    }
    PdfAssertions.validInvoice(service.invoice(winner.invoiceId()));
  }

  @RepeatedTest(5)
  void twentyRegenerationsAgreeOnOneCompletePdf() throws Exception {
    var dao = new JdbcFinanceDao();
    var service = finance(dao);
    long obligation = obligation(service, BigDecimal.ONE);
    var request = request(obligation, BigDecimal.ONE, MysqlFixture.unique());
    PaymentResult payment = service.confirmPayment(request);
    assertTrue(payment.pdfGenerated());
    long invoiceId = payment.invoiceId();
    Invoice before = service.invoice(invoiceId);
    Path destination = Path.of(before.path());
    List<Runnable> tasks = new ArrayList<>();
    for (int index = 0; index < 20; index++) {
      tasks.add(
          () -> {
            // Separate service instances exercise the same process-wide synchronization as the CLI.
            PaymentResult regenerated = finance(dao).regenerate(invoiceId);
            assertTrue(regenerated.pdfGenerated(), regenerated.message());
            assertEquals(payment.paymentId(), regenerated.paymentId());
          });
    }
    assertEquals(20, compete(tasks, null));
    Invoice after = service.invoice(invoiceId);
    assertEquals(before.cufe(), after.cufe());
    assertEquals(destination, Path.of(after.path()));
    PdfAssertions.validInvoice(after);
    try (var files = Files.list(directory)) {
      assertEquals(List.of(destination), files.toList());
    }
  }

  @Test
  void twoThousandOperationsLeaveNoMysqlSessionsOpen() throws Exception {
    var service = new CatalogService(connections, new JdbcCatalogDao());
    try (Connection probe = connections.open()) {
      String server = Jdbc.query(probe, "SELECT VERSION()", row -> row.getString(1)).get(0);
      assertTrue(server.startsWith("8.0."), server);
      long baseline = connectedThreads(probe);
      for (int index = 0; index < 2_000; index++) {
        if (index % 2 == 0) {
          assertFalse(service.list(CatalogType.GRADE).isEmpty());
        } else {
          var rejected =
              assertThrows(
                  BusinessException.class,
                  () ->
                      service.update(
                          CatalogType.RESOURCE_CATEGORY,
                          new CatalogEntry(Integer.MAX_VALUE, "Missing category", 0, true)));
          assertNull(rejected.getCause());
        }
      }
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      long remaining = connectedThreads(probe);
      while (remaining > baseline && System.nanoTime() < deadline) {
        Thread.sleep(20);
        remaining = connectedThreads(probe);
      }
      assertEquals(baseline, remaining, "MySQL Threads_connected after 2,000 transactions");
      System.out.printf(
          "QA JDBC: MySQL %s, Threads_connected before=%d after=%d%n", server, baseline, remaining);
    }
  }

  private static long connectedThreads(Connection probe) throws SQLException {
    return Jdbc.query(probe, "SHOW GLOBAL STATUS LIKE 'Threads_connected'", row -> row.getLong(2))
        .get(0);
  }

  private FinanceService finance(JdbcFinanceDao dao) {
    return new FinanceService(
        connections, dao, new PdfInvoiceGenerator(directory), SchoolInformation.fromEnvironment());
  }

  private static long obligation(FinanceService service, BigDecimal amount) {
    long concept =
        service.createConcept(MysqlFixture.unique(), "Concurrent payment", amount, BigDecimal.ZERO);
    return service.createObligation(1, concept, LocalDate.now(), MysqlFixture.unique());
  }

  private static PaymentRequest request(long obligation, BigDecimal amount, String receipt) {
    return new PaymentRequest(obligation, 1, amount, LocalDate.now(), PaymentMethod.CASH, receipt);
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
                    if (expectedMessage == null || !expectedMessage.equals(rejection.getMessage()))
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
