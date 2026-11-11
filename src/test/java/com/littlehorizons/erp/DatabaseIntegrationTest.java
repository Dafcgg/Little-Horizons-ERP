package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.*;
import java.io.IOException;
import java.math.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "ERP_TEST_DB_URL", matches = ".+")
class DatabaseIntegrationTest {
  private final ConnectionProvider connections =
      () ->
          DriverManager.getConnection(
              System.getenv("ERP_TEST_DB_URL"),
              System.getenv().getOrDefault("ERP_TEST_DB_USER", "root"),
              System.getenv("ERP_TEST_DB_PASSWORD"));
  @TempDir Path directory;

  private String unique() {
    return UUID.randomUUID().toString().substring(0, 12);
  }

  private long count(String sql, Object... values) throws SQLException {
    try (Connection c = connections.open()) {
      return Jdbc.count(c, sql, values);
    }
  }

  private long student() {
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

  @Test
  void everyTableHasAtLeastThirtyRows() throws Exception {
    try (Connection c = connections.open()) {
      var tables =
          Jdbc.query(
              c,
              "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND"
                  + " TABLE_TYPE='BASE TABLE'",
              r -> r.getString(1));
      assertEquals(26, tables.size());
      for (String table : tables) {
        assertTrue(table.matches("[a-z_]+"));
        assertTrue(Jdbc.count(c, "SELECT COUNT(*) FROM " + table) >= 30, table);
      }
    }
  }

  @Test
  void failedGuardianLinkRollsBackStudentInsert() throws Exception {
    String document = unique();
    var service = new StudentService(connections, new JdbcStudentDao());
    assertThrows(
        BusinessException.class,
        () ->
            service.register(
                new Student(
                    0, document, "Test", "Rollback", LocalDate.of(2015, 1, 1), "Street", "Activo"),
                99999999,
                "Tutor"));
    assertEquals(0, count("SELECT COUNT(*) FROM estudiantes WHERE numero_documento=?", document));
  }

  @Test
  void databaseEnforcesGradeCheckEvenWithoutService() throws Exception {
    try (Connection c = connections.open()) {
      assertThrows(
          SQLException.class,
          () ->
              Jdbc.update(c, "UPDATE registros_academicos SET valor_nota=5.1 WHERE id_registro=1"));
    }
  }

  @Test
  void lastClubSeatCannotBeOversold() throws Exception {
    var service = new ActivityService(connections, new JdbcActivityDao());
    long club = service.createClub(new Club(0, "Test " + unique(), 1, 1));
    List<Boolean> results = race(() -> service.join(club, 1), () -> service.join(club, 2));
    assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
    assertEquals(
        1,
        count(
            "SELECT COUNT(*) FROM membresias_club WHERE id_club=? AND estado_membresia='Activa'",
            club));
  }

  @Test
  void clubRetirementReleasesSeatAndPreservesHistory() throws Exception {
    var service = new ActivityService(connections, new JdbcActivityDao());
    long club = service.createClub(new Club(0, "Test " + unique(), 1, 1));
    service.join(club, 1);
    assertThrows(BusinessException.class, () -> service.join(club, 1));
    service.withdraw(club, 1);
    service.join(club, 1);
    assertEquals(2, count("SELECT COUNT(*) FROM membresias_club WHERE id_club=?", club));
  }

  @Test
  void loanExclusivitySpansStudentsAndTeachers() throws Exception {
    var service = new ResourceService(connections, new JdbcResourceDao());
    long resource =
        service.create(new SchoolResource(0, unique(), "Test resource", 1, "Room", "Disponible"));
    List<Boolean> results =
        race(
            () -> service.lend(resource, 1, BorrowerType.STUDENT, LocalDate.now().plusDays(1)),
            () -> service.lend(resource, 1, BorrowerType.TEACHER, LocalDate.now().plusDays(1)));
    assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
    try (Connection c = connections.open()) {
      for (BorrowerType type : BorrowerType.values()) {
        var loans =
            Jdbc.query(
                c,
                "SELECT id_prestamo FROM " + type.databaseValue() + " WHERE id_recurso=?",
                r -> r.getLong(1),
                resource);
        if (!loans.isEmpty()) service.returnLoan(loans.get(0), type);
      }
    }
    long loan = service.lend(resource, 1, BorrowerType.STUDENT, LocalDate.now().plusDays(1));
    assertTrue(loan > 0);
  }

  @Test
  void invalidBorrowerRollsBackResourceState() throws Exception {
    var service = new ResourceService(connections, new JdbcResourceDao());
    long resource =
        service.create(new SchoolResource(0, unique(), "Test rollback", 1, "Room", "Disponible"));
    assertThrows(
        BusinessException.class,
        () -> service.lend(resource, 99999999, BorrowerType.STUDENT, LocalDate.now().plusDays(1)));
    assertTrue(
        service.resources().stream()
            .anyMatch(r -> r.id() == resource && r.status().equals("Disponible")));
  }

  @Test
  void paymentSurvivesFileFailureAndRetryDoesNotDuplicateIt() throws Exception {
    var dao = new JdbcFinanceDao();
    long student = student();
    var failing =
        new FinanceService(
            connections,
            dao,
            invoice -> {
              throw new IOException("Simulated disk failure");
            },
            SchoolInformation.fromEnvironment());
    long concept =
        failing.createConcept(
            unique(), "Test concept", new BigDecimal("100.00"), new BigDecimal("0.19"));
    failing.setFee(student, concept, new BigDecimal("80.00"));
    long obligation =
        failing.createObligation(student, concept, LocalDate.now().minusMonths(2), unique());
    String receipt = unique();
    var request =
        new PaymentRequest(
            obligation,
            1,
            new BigDecimal("95.20"),
            LocalDate.now(),
            PaymentMethod.TRANSFER,
            receipt);
    PaymentResult first = failing.confirmPayment(request);
    assertFalse(first.pdfGenerated());
    assertEquals(1, count("SELECT COUNT(*) FROM pagos WHERE numero_recibo=?", receipt));
    var working =
        new FinanceService(
            connections,
            dao,
            new PdfInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    PaymentResult retried = working.confirmPayment(request);
    assertTrue(retried.pdfGenerated());
    assertEquals(first.paymentId(), retried.paymentId());
    assertEquals(first.invoiceId(), retried.invoiceId());
    assertEquals(1, count("SELECT COUNT(*) FROM facturas WHERE id_pago=?", first.paymentId()));
    try (Connection c = connections.open()) {
      Invoice invoice = dao.invoice(c, first.invoiceId()).orElseThrow();
      assertEquals(new BigDecimal("80.00"), invoice.subtotal());
      assertEquals(new BigDecimal("15.20"), invoice.tax());
      try (var pdf = Loader.loadPDF(Path.of(invoice.path()).toFile())) {
        String text = new PDFTextStripper().getText(pdf);
        assertTrue(text.contains("SIN VALIDACION DIAN"));
        assertTrue(text.contains(invoice.cufe()));
        assertTrue(text.contains("95.20"));
        boolean decoded = false;
        for (var name : pdf.getPage(0).getResources().getXObjectNames()) {
          var object = pdf.getPage(0).getResources().getXObject(name);
          if (object instanceof PDImageXObject image) {
            String payload =
                new MultiFormatReader()
                    .decode(
                        new BinaryBitmap(
                            new HybridBinarizer(
                                new BufferedImageLuminanceSource(image.getImage()))))
                    .getText();
            assertEquals(PdfInvoiceGenerator.qrPayload(invoice), payload);
            decoded = true;
          }
        }
        assertTrue(decoded);
      }
    }
    assertThrows(
        BusinessException.class,
        () ->
            working.confirmPayment(
                new PaymentRequest(
                    obligation,
                    1,
                    new BigDecimal("1.00"),
                    LocalDate.now(),
                    PaymentMethod.TRANSFER,
                    receipt)));
  }

  @Test
  void unpaidObligationAppearsInOverdueReport() {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new PdfInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long obligation =
        service.createObligation(student(), 1, LocalDate.now().minusMonths(2), unique());
    assertTrue(
        service.overdue().stream()
            .anyMatch(b -> b.obligationId() == obligation && b.balance().signum() > 0));
  }

  @Test
  void concurrentPaymentsCannotExceedOutstandingBalance() throws Exception {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new PdfInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long obligation = service.createObligation(student(), 1, LocalDate.now(), unique());
    List<Boolean> results =
        race(
            () ->
                service.confirmPayment(
                    new PaymentRequest(
                        obligation,
                        1,
                        new BigDecimal("350000.00"),
                        LocalDate.now(),
                        PaymentMethod.CASH,
                        unique())),
            () ->
                service.confirmPayment(
                    new PaymentRequest(
                        obligation,
                        1,
                        new BigDecimal("350000.00"),
                        LocalDate.now(),
                        PaymentMethod.CASH,
                        unique())));
    assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
    assertEquals(1, count("SELECT COUNT(*) FROM pagos WHERE id_obligacion=?", obligation));
  }

  @Test
  void academicEnrollmentTeacherAssignmentGradesAndAttendanceWorkTogether() {
    long student = student();
    new StudentService(connections, new JdbcStudentDao()).enroll(student, 1, 2026);
    var service = new AcademicService(connections, new JdbcAcademicDao());
    long teacher =
        service.registerTeacher(
            new Teacher(0, unique(), "Test", "Teacher", unique() + "@example.invalid", "Science"));
    long subject = service.registerSubject(new Subject(0, unique(), "Test subject", 1));
    String section = unique().substring(0, 6);
    service.assign(teacher, subject, 2026, section, 3, LocalTime.of(10, 0), LocalTime.of(11, 0));
    assertThrows(
        BusinessException.class,
        () ->
            service.assign(
                teacher, subject, 2026, section, 3, LocalTime.of(10, 30), LocalTime.of(11, 30)));
    service.subscribe(student, subject, 2026, AcademicPeriod.I, section);
    service.recordGrade(
        new AcademicRecord(
            student, subject, teacher, 2026, AcademicPeriod.I, new BigDecimal("4.5")));
    service.recordGrade(
        new AcademicRecord(
            student, subject, teacher, 2026, AcademicPeriod.I, new BigDecimal("4.8")));
    assertEquals(
        new BigDecimal("4.8"), service.subjectAverage(student, subject, 2026).orElseThrow());
    assertEquals(
        new BigDecimal("4.8"),
        service.courseAverage(subject, 2026, AcademicPeriod.I, section).orElseThrow());
    for (int day = 2; day < 6; day++)
      service.recordAttendance(
          student,
          subject,
          teacher,
          2026,
          AcademicPeriod.I,
          LocalDate.of(2026, 2, day),
          AttendanceStatus.ABSENT);
    assertTrue(
        service.absences(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 8), 3).stream()
            .anyMatch(s -> s.startsWith("Estudiante " + student + ":")));
    assertThrows(
        BusinessException.class,
        () ->
            service.recordGrade(
                new AcademicRecord(
                    student, subject, 1, 2026, AcademicPeriod.I, new BigDecimal("4.0"))));
  }

  @Test
  void nonCompetitiveEventsRejectPositionsAndParticipantsAreUnique() {
    var service = new ActivityService(connections, new JdbcActivityDao());
    long event =
        service.createEvent(
            new SchoolEvent(0, "Test " + unique(), LocalDate.now(), "Cultural", false));
    assertThrows(BusinessException.class, () -> service.participate(event, 1, "1er Lugar"));
    service.participate(event, 1, "");
    assertThrows(BusinessException.class, () -> service.participate(event, 1, ""));
  }

  @Test
  void invoiceInsertFailureRollsBackThePayment() throws Exception {
    SchoolInformation invalidSchool =
        new SchoolInformation("X".repeat(300), "DEMO", "Street", "Phone", "school@example.invalid");
    var service =
        new FinanceService(
            connections, new JdbcFinanceDao(), new PdfInvoiceGenerator(directory), invalidSchool);
    long obligation = service.createObligation(student(), 1, LocalDate.now(), unique());
    String receipt = unique();
    assertThrows(
        BusinessException.class,
        () ->
            service.confirmPayment(
                new PaymentRequest(
                    obligation,
                    1,
                    new BigDecimal("10.00"),
                    LocalDate.now(),
                    PaymentMethod.CASH,
                    receipt)));
    assertEquals(0, count("SELECT COUNT(*) FROM pagos WHERE numero_recibo=?", receipt));
    assertEquals(0, count("SELECT COUNT(*) FROM pagos WHERE id_obligacion=?", obligation));
  }

  @Test
  void partialPaymentsSettleDebtAndInvoiceRetainsCustomerSnapshot() throws Exception {
    var students = new StudentService(connections, new JdbcStudentDao());
    long guardian =
        students.registerGuardian(
            new Guardian(0, unique(), "Original", "Guardian", "3100000000", "Original street"));
    long student =
        students.register(
            new Student(
                0, unique(), "Test", "Snapshot", LocalDate.of(2015, 1, 1), "Street", "Activo"),
            guardian,
            "Tutor");
    var dao = new JdbcFinanceDao();
    var service =
        new FinanceService(
            connections,
            dao,
            new PdfInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long concept =
        service.createConcept(
            unique(), "Partial payment concept", new BigDecimal("100.00"), BigDecimal.ZERO);
    long obligation =
        service.createObligation(student, concept, LocalDate.now().minusMonths(2), unique());
    PaymentResult first =
        service.confirmPayment(
            new PaymentRequest(
                obligation,
                guardian,
                new BigDecimal("40.00"),
                LocalDate.now(),
                PaymentMethod.CASH,
                unique()));
    assertTrue(first.pdfGenerated());
    assertEquals(
        new BigDecimal("60.00"),
        service.overdue().stream()
            .filter(b -> b.obligationId() == obligation)
            .findFirst()
            .orElseThrow()
            .balance());
    try (Connection c = connections.open()) {
      Jdbc.update(
          c,
          "UPDATE acudientes SET primer_nombre='Changed',direccion='Changed street' WHERE"
              + " id_acudiente=?",
          guardian);
      Invoice snapshot = dao.invoice(c, first.invoiceId()).orElseThrow();
      assertEquals("Original Guardian", snapshot.customerName());
      assertEquals("Original street", snapshot.customerAddress());
    }
    assertTrue(
        service
            .confirmPayment(
                new PaymentRequest(
                    obligation,
                    guardian,
                    new BigDecimal("60.00"),
                    LocalDate.now(),
                    PaymentMethod.CASH,
                    unique()))
            .pdfGenerated());
    assertTrue(service.overdue().stream().noneMatch(b -> b.obligationId() == obligation));
  }

  @Test
  void invalidDiscountedFeesAndOverpaymentsAreRejected() throws Exception {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new PdfInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long student = student();
    long concept =
        service.createConcept(
            unique(), "Discount audit", new BigDecimal("100.00"), BigDecimal.ZERO);
    assertThrows(
        BusinessException.class, () -> service.setFee(student, concept, new BigDecimal("-0.01")));
    service.setFee(student, concept, BigDecimal.ZERO);
    long free = service.createObligation(student, concept, LocalDate.now(), unique());
    assertThrows(
        BusinessException.class,
        () ->
            service.confirmPayment(
                new PaymentRequest(
                    free,
                    1,
                    new BigDecimal("0.01"),
                    LocalDate.now(),
                    PaymentMethod.CASH,
                    unique())));
    service.setFee(student, concept, new BigDecimal("70.00"));
    long discounted = service.createObligation(student, concept, LocalDate.now(), unique());
    try (Connection c = connections.open()) {
      assertEquals(
          new BigDecimal("70.00"),
          Jdbc.query(
                  c,
                  "SELECT monto FROM obligaciones WHERE id_obligacion=?",
                  r -> r.getBigDecimal(1),
                  discounted)
              .get(0));
      assertThrows(
          SQLException.class,
          () ->
              Jdbc.update(c, "UPDATE obligaciones SET monto=-1 WHERE id_obligacion=?", discounted));
      assertThrows(
          SQLException.class,
          () ->
              Jdbc.update(
                  c,
                  "UPDATE tarifas_estudiante SET monto=-1 WHERE id_estudiante=? AND id_concepto=?",
                  student,
                  concept));
    }
    assertThrows(
        BusinessException.class,
        () ->
            service.confirmPayment(
                new PaymentRequest(
                    discounted,
                    1,
                    new BigDecimal("70.01"),
                    LocalDate.now(),
                    PaymentMethod.CASH,
                    unique())));
    assertEquals(0, count("SELECT COUNT(*) FROM pagos WHERE id_obligacion=?", discounted));
  }

  @Test
  void grossPriceOverflowIsRejectedBeforeObligationInsert() throws Exception {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new PdfInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long student = student();
    long concept =
        service.createConcept(
            unique(), "Overflow audit", new BigDecimal("99999999.99"), BigDecimal.ONE);
    assertThrows(
        BusinessException.class,
        () -> service.createObligation(student, concept, LocalDate.now(), unique()));
    assertEquals(
        0,
        count(
            "SELECT COUNT(*) FROM obligaciones WHERE id_estudiante=? AND id_concepto=?",
            student,
            concept));
  }

  @Test
  void inactiveEnrollmentBlocksNewGradesAndAttendance() throws Exception {
    long student = student();
    var students = new StudentService(connections, new JdbcStudentDao());
    students.enroll(student, 1, 2026);
    var service = new AcademicService(connections, new JdbcAcademicDao());
    service.subscribe(student, 1, 2026, AcademicPeriod.I, "A");
    try (Connection c = connections.open()) {
      Jdbc.update(
          c, "UPDATE matriculas SET estado_matricula='Retirada' WHERE id_estudiante=?", student);
    }
    assertThrows(
        BusinessException.class,
        () ->
            service.recordGrade(
                new AcademicRecord(student, 1, 1, 2026, AcademicPeriod.I, new BigDecimal("4.0"))));
    assertThrows(
        BusinessException.class,
        () ->
            service.recordAttendance(
                student,
                1,
                1,
                2026,
                AcademicPeriod.I,
                LocalDate.of(2026, 2, 2),
                AttendanceStatus.PRESENT));
    try (Connection c = connections.open()) {
      Jdbc.update(c, "UPDATE estudiantes SET estado='Retirado' WHERE id_estudiante=?", student);
    }
    assertThrows(BusinessException.class, () -> students.enroll(student, 1, 2027));
  }

  @Test
  void chequePaymentMatchesDatabaseEnumAndIsIdempotent() throws Exception {
    var service =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new PdfInvoiceGenerator(directory),
            SchoolInformation.fromEnvironment());
    long obligation = service.createObligation(student(), 1, LocalDate.now(), unique());
    var request =
        new PaymentRequest(
            obligation, 1, new BigDecimal("1.00"), LocalDate.now(), PaymentMethod.CHEQUE, unique());
    var first = service.confirmPayment(request);
    var second = service.confirmPayment(request);
    assertTrue(first.pdfGenerated());
    assertTrue(second.pdfGenerated());
    assertEquals(first.paymentId(), second.paymentId());
    try (Connection c = connections.open()) {
      var invoice = new JdbcFinanceDao().invoice(c, first.invoiceId()).orElseThrow();
      assertEquals(
          FinanceService.cufe(
              invoice.number(), invoice.schoolNit(), invoice.date(), invoice.total()),
          invoice.cufe());
      assertEquals(
          Path.of(invoice.path()).getFileName().toString(),
          Jdbc.query(
                  c,
                  "SELECT nombre_archivo_pdf FROM facturas WHERE id_factura=?",
                  r -> r.getString(1),
                  invoice.id())
              .get(0));
    }
  }

  private List<Boolean> race(Runnable first, Runnable second) throws Exception {
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
