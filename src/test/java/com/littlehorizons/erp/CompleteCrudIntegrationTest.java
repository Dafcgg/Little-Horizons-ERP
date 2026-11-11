package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.controller.*;
import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.*;
import com.littlehorizons.erp.view.ConsoleView;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "ERP_TEST_DB_URL", matches = ".+")
class CompleteCrudIntegrationTest {
  private final ConnectionProvider connections =
      () ->
          DriverManager.getConnection(
              System.getenv("ERP_TEST_DB_URL"),
              System.getenv().getOrDefault("ERP_TEST_DB_USER", "root"),
              System.getenv("ERP_TEST_DB_PASSWORD"));
  private final StudentService students = new StudentService(connections, new JdbcStudentDao());
  private final AcademicService academics = new AcademicService(connections, new JdbcAcademicDao());
  private final ActivityService activities =
      new ActivityService(connections, new JdbcActivityDao());
  private final ResourceService resources = new ResourceService(connections, new JdbcResourceDao());
  private final CatalogService catalogs = new CatalogService(connections, new JdbcCatalogDao());
  @TempDir Path directory;

  @BeforeAll
  static void logging() throws Exception {
    AppLogger.configure();
  }

  private String unique() {
    return UUID.randomUUID().toString().substring(0, 12);
  }

  private BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  private FinanceService finance() {
    return new FinanceService(
        connections,
        new JdbcFinanceDao(),
        new PdfInvoiceGenerator(directory),
        SchoolInformation.fromEnvironment());
  }

  private long guardian() {
    return students.registerGuardian(
        new Guardian(0, unique(), "Test", "Guardian", "3100000000", "Street"));
  }

  private long student(long guardian) {
    return students.register(
        new Student(0, unique(), "Test", "Student", LocalDate.of(2015, 1, 1), "Street", "Activo"),
        guardian,
        "Tutor");
  }

  private long teacher() {
    return academics.registerTeacher(
        new Teacher(0, unique(), "Test", "Teacher", unique() + "@example.invalid", "Math"));
  }

  private long subject() {
    return academics.registerSubject(new Subject(0, unique(), "Test subject", 1));
  }

  private record Course(long student, long teacher, long subject, String section) {}

  private Course course() {
    long student = student(guardian()), teacher = teacher(), subject = subject();
    String section = unique().substring(0, 6);
    students.enroll(student, 1, 2026);
    academics.assign(teacher, subject, 2026, section, 4, LocalTime.of(10, 0), LocalTime.of(11, 0));
    academics.subscribe(student, subject, 2026, AcademicPeriod.I, section);
    return new Course(student, teacher, subject, section);
  }

  private PaymentRequest request(long obligation, long guardian, String amount) {
    return new PaymentRequest(
        obligation, guardian, money(amount), LocalDate.now(), PaymentMethod.CASH, unique());
  }

  @Test
  void studentGuardianAndLinkCrudPreservesLastActiveGuardian() {
    long guardian = guardian(), student = student(guardian);
    GuardianLink initial = students.links(student).get(0);
    assertTrue(initial.primary());
    assertTrue(initial.emergency());
    assertTrue(initial.financial());
    Student old = students.student(student);
    students.updateStudent(
        new Student(
            student,
            old.document(),
            "Updated",
            old.lastName(),
            old.birthDate(),
            "New street",
            "Activo"));
    assertEquals("Updated", students.student(student).firstName());
    Guardian g = students.guardian(guardian);
    students.updateGuardian(
        new Guardian(
            guardian, g.document(), "Updated", g.lastName(), "3110000000", g.address(), true));
    assertEquals("Updated", students.guardian(guardian).firstName());
    assertThrows(BusinessException.class, () -> students.deactivateGuardian(guardian));
    assertThrows(BusinessException.class, () -> students.removeLink(student, guardian));
    long second = guardian();
    students.saveLink(new GuardianLink(student, second, "Madre", true, true, true));
    assertEquals(2, students.links(student).size());
    assertEquals(1, students.links(student).stream().filter(GuardianLink::primary).count());
    students.deactivateGuardian(guardian);
    assertFalse(students.guardian(guardian).active());
    students.removeLink(student, guardian);
    assertEquals(1, students.links(student).size());
    students.deactivateStudent(student);
    assertEquals("Retirado", students.student(student).status());
    assertEquals(1, students.links(student).size());
  }

  @Test
  void catalogsSupportCreateReadUpdateDeactivateAndReactivate() {
    for (CatalogType type : CatalogType.values()) {
      long id =
          catalogs.create(
              type,
              new CatalogEntry(0, "Test " + unique(), type == CatalogType.GRADE ? 2 : 0, true));
      CatalogEntry old = catalogs.get(type, id);
      catalogs.update(type, new CatalogEntry(id, "Renamed " + unique(), old.level(), true));
      assertTrue(catalogs.get(type, id).name().startsWith("Renamed"));
      catalogs.deactivate(type, id);
      assertFalse(catalogs.get(type, id).active());
      CatalogEntry inactive = catalogs.get(type, id);
      catalogs.update(type, new CatalogEntry(id, inactive.name(), inactive.level(), true));
      assertTrue(catalogs.get(type, id).active());
    }
  }

  @Test
  void enrollmentCrudDoesNotMoveAnExistingAcademicHistory() {
    Course c = course();
    Enrollment enrollment = students.enrollments(c.student()).get(0);
    students.updateEnrollment(enrollment.id(), 1, "Completada");
    assertEquals("Completada", students.enrollments(c.student()).get(0).status());
    assertThrows(
        BusinessException.class, () -> students.updateEnrollment(enrollment.id(), 2, "Activa"));
    students.updateEnrollment(enrollment.id(), 1, "Activa");
    students.withdrawEnrollment(enrollment.id());
    assertEquals("Retirada", students.enrollments(c.student()).get(0).status());
  }

  @Test
  void teachersAndSubjectsSupportEditInactivationAndReactivation() {
    long teacher = teacher(), subject = subject();
    Teacher t = academics.teacher(teacher);
    academics.updateTeacher(
        new Teacher(teacher, t.document(), "Edited", t.lastName(), t.email(), "Physics", "Activo"));
    assertEquals("Edited", academics.teacher(teacher).firstName());
    academics.deactivateTeacher(teacher);
    assertEquals("Inactivo", academics.teacher(teacher).status());
    academics.updateTeacher(
        new Teacher(
            teacher,
            t.document(),
            t.firstName(),
            t.lastName(),
            t.email(),
            t.specialization(),
            "Activo"));
    Subject s = academics.subject(subject);
    academics.updateSubject(new Subject(subject, s.code(), "Edited subject", 1, true));
    assertEquals("Edited subject", academics.subject(subject).name());
    academics.deactivateSubject(subject);
    assertFalse(academics.subject(subject).active());
    academics.updateSubject(new Subject(subject, s.code(), s.name(), 1, true));
    assertTrue(academics.subject(subject).active());
  }

  @Test
  void assignmentsSchedulesAndSubscriptionsHaveCompleteLifecycle() {
    Course c = course();
    TeachingAssignment assignment =
        academics.assignments().stream()
            .filter(a -> a.subjectId() == c.subject())
            .findFirst()
            .orElseThrow();
    ClassSchedule schedule =
        academics.schedules().stream()
            .filter(s -> s.assignmentId() == assignment.id())
            .findFirst()
            .orElseThrow();
    academics.updateSchedule(schedule.id(), 5, LocalTime.of(9, 0), LocalTime.of(10, 0));
    assertEquals(
        5,
        academics.schedules().stream()
            .filter(s -> s.id() == schedule.id())
            .findFirst()
            .orElseThrow()
            .day());
    Subscription subscription = academics.subscriptions(c.student()).get(0);
    academics.updateSubscription(subscription.id(), c.section(), "Completada");
    assertEquals("Completada", academics.subscriptions(c.student()).get(0).status());
    academics.updateSubscription(subscription.id(), c.section(), "Activa");
    academics.deactivateAssignment(assignment.id());
    assertFalse(
        academics.assignments().stream()
            .filter(a -> a.id() == assignment.id())
            .findFirst()
            .orElseThrow()
            .active());
    academics.updateAssignment(
        new TeachingAssignment(
            assignment.id(),
            assignment.teacherId(),
            assignment.subjectId(),
            assignment.year(),
            assignment.section(),
            true));
    assertThrows(
        BusinessException.class,
        () ->
            academics.updateAssignment(
                new TeachingAssignment(
                    assignment.id(), teacher(), assignment.subjectId(), 2026, c.section(), true)));
    academics.withdrawSubscription(subscription.id());
    assertEquals("Retirada", academics.subscriptions(c.student()).get(0).status());
    academics.deleteSchedule(schedule.id());
    assertTrue(academics.schedules().stream().noneMatch(s -> s.id() == schedule.id()));
  }

  @Test
  void gradeAndAttendanceCrudInvalidatesDerivedFinal() {
    Course c = course();
    for (AcademicPeriod period : List.of(AcademicPeriod.II, AcademicPeriod.III, AcademicPeriod.IV))
      academics.subscribe(c.student(), c.subject(), 2026, period, c.section());
    for (AcademicPeriod period :
        List.of(AcademicPeriod.I, AcademicPeriod.II, AcademicPeriod.III, AcademicPeriod.IV))
      academics.recordGrade(
          new AcademicRecord(c.student(), c.subject(), c.teacher(), 2026, period, money("4.0")));
    assertEquals(money("4.0"), academics.closeSubject(c.student(), c.subject(), 2026));
    assertEquals(5, academics.history(c.student()).size());
    academics.recordGrade(
        new AcademicRecord(
            c.student(), c.subject(), c.teacher(), 2026, AcademicPeriod.I, money("5.0")));
    assertEquals(4, academics.history(c.student()).size());
    assertThrows(
        BusinessException.class,
        () ->
            academics.recordGrade(
                new AcademicRecord(
                    c.student(), c.subject(), c.teacher(), 2026, AcademicPeriod.I, money("3.45"))));
    academics.deleteGrade(c.student(), c.subject(), 2026, AcademicPeriod.II);
    assertThrows(
        BusinessException.class, () -> academics.closeSubject(c.student(), c.subject(), 2026));
    LocalDate date = LocalDate.of(2026, 2, 2);
    academics.recordAttendance(
        c.student(),
        c.subject(),
        c.teacher(),
        2026,
        AcademicPeriod.I,
        date,
        AttendanceStatus.ABSENT);
    academics.recordAttendance(
        c.student(),
        c.subject(),
        c.teacher(),
        2026,
        AcademicPeriod.I,
        date,
        AttendanceStatus.PRESENT);
    AttendanceRecord record = academics.attendances(c.student()).get(0);
    assertEquals(AttendanceStatus.PRESENT, record.status());
    academics.deleteAttendance(record.id());
    assertTrue(academics.attendances(c.student()).isEmpty());
  }

  @Test
  void clubMembershipCrudGuardsCapacityAndRetiresMembersOnInactivation() {
    long teacher = teacher(), first = student(guardian()), second = student(guardian());
    long club = activities.createClub(new Club(0, "Test " + unique(), teacher, 1));
    activities.join(club, first);
    assertThrows(
        BusinessException.class,
        () -> activities.updateClub(new Club(club, "Invalid", teacher, 0, true)));
    ClubMembership membership = activities.memberships(club).get(0);
    activities.updateMembership(membership.id(), LocalDate.now().minusDays(1), "Retirada");
    activities.join(club, second);
    assertThrows(
        BusinessException.class,
        () -> activities.updateMembership(membership.id(), LocalDate.now(), "Activa"));
    activities.deactivateClub(club);
    assertFalse(activities.club(club).active());
    assertTrue(activities.memberships(club).stream().allMatch(m -> m.status().equals("Retirada")));
    activities.updateClub(new Club(club, "Edited " + unique(), teacher, 2, true));
    assertEquals(2, activities.club(club).capacity());
    activities.updateMembership(membership.id(), LocalDate.now(), "Activa");
    assertEquals(
        1, activities.memberships(club).stream().filter(m -> m.status().equals("Activa")).count());
  }

  @Test
  void eventAndParticipantCrudKeepsResultsConsistent() {
    long student = student(guardian());
    long event =
        activities.createEvent(
            new SchoolEvent(0, "Test " + unique(), LocalDate.now(), "Cultural", true));
    activities.participate(event, student, "Participante");
    activities.updateParticipation(new EventParticipation(event, student, "1er Lugar"));
    assertEquals("1er Lugar", activities.participants(event).get(0).position());
    assertThrows(
        BusinessException.class,
        () ->
            activities.updateEvent(
                new SchoolEvent(event, "Updated", LocalDate.now(), "Cultural", false, true)));
    activities.updateParticipation(new EventParticipation(event, student, ""));
    activities.updateEvent(
        new SchoolEvent(event, "Updated", LocalDate.now(), "Social", false, true));
    assertEquals("Updated", activities.event(event).name());
    activities.removeParticipant(event, student);
    assertTrue(activities.participants(event).isEmpty());
    activities.deactivateEvent(event);
    assertFalse(activities.event(event).active());
    assertThrows(BusinessException.class, () -> activities.participate(event, student, ""));
  }

  @Test
  void conceptsFeesAndObligationsSupportEditAndCancellation() {
    var finance = finance();
    long guardian = guardian(), student = student(guardian);
    long concept = finance.createConcept(unique(), "Concept", money("100.00"), BigDecimal.ZERO);
    PaymentConcept c = finance.concept(concept);
    finance.updateConcept(
        new PaymentConcept(concept, c.code(), "Edited", money("120.00"), BigDecimal.ZERO, true));
    assertEquals("Edited", finance.concept(concept).name());
    finance.setFee(student, concept, money("80.00"));
    assertEquals(money("80.00"), finance.fees(student).get(0).amount());
    finance.setFee(student, concept, money("70.00"));
    assertEquals(money("70.00"), finance.fees(student).get(0).amount());
    finance.removeFee(student, concept);
    assertTrue(finance.fees(student).isEmpty());
    long obligation = finance.createObligation(student, concept, LocalDate.now(), unique());
    assertEquals(money("120.00"), finance.obligation(obligation).amount());
    finance.updateObligation(
        obligation, "Updated " + unique(), LocalDate.now().plusDays(5), money("110.00"));
    assertEquals(money("110.00"), finance.obligation(obligation).amount());
    finance.cancelObligation(obligation);
    assertEquals("Anulada", finance.obligation(obligation).status());
    assertThrows(
        BusinessException.class,
        () -> finance.confirmPayment(request(obligation, guardian, "1.00")));
    finance.deactivateConcept(concept);
    assertFalse(finance.concept(concept).active());
    assertThrows(
        BusinessException.class,
        () -> finance.createObligation(student, concept, LocalDate.now(), unique()));
  }

  @Test
  void voidingPaymentsRestoresBalanceAndMarksPdfAsCancelled() throws Exception {
    var finance = finance();
    long guardian = guardian(), student = student(guardian);
    long concept = finance.createConcept(unique(), "Concept", money("100.00"), BigDecimal.ZERO);
    long obligation =
        finance.createObligation(student, concept, LocalDate.now().minusMonths(2), unique());
    var original = finance.confirmPayment(request(obligation, guardian, "60.00"));
    assertTrue(original.pdfGenerated());
    assertThrows(
        BusinessException.class,
        () -> finance.updateObligation(obligation, "Invalid", LocalDate.now(), money("59.00")));
    assertThrows(BusinessException.class, () -> finance.cancelObligation(obligation));
    var cancelled = finance.voidPayment(original.paymentId(), "Duplicate payment");
    assertTrue(cancelled.pdfGenerated());
    assertEquals("Anulado", finance.payment(original.paymentId()).status());
    Invoice invoice = finance.invoice(original.invoiceId());
    assertEquals("Anulada", invoice.businessStatus());
    assertEquals(
        money("100.00"),
        finance.overdue().stream()
            .filter(b -> b.obligationId() == obligation)
            .findFirst()
            .orElseThrow()
            .balance());
    try (var pdf = Loader.loadPDF(Path.of(invoice.path()).toFile())) {
      assertTrue(new PDFTextStripper().getText(pdf).contains("FACTURA ANULADA"));
    }
    assertEquals(
        original.invoiceId(), finance.voidInvoice(original.invoiceId(), "Retry").invoiceId());
    finance.cancelObligation(obligation);
    assertTrue(finance.overdue().stream().noneMatch(b -> b.obligationId() == obligation));
  }

  @Test
  void correctingPaymentIsAtomicIdempotentAndPreservesOriginal() {
    var finance = finance();
    long guardian = guardian(), student = student(guardian);
    long concept = finance.createConcept(unique(), "Concept", money("100.00"), BigDecimal.ZERO);
    long obligation =
        finance.createObligation(student, concept, LocalDate.now().minusMonths(2), unique());
    var original = finance.confirmPayment(request(obligation, guardian, "60.00"));
    var replacement = request(obligation, guardian, "80.00");
    var corrected = finance.correctPayment(original.paymentId(), replacement, "Amount corrected");
    assertTrue(corrected.pdfGenerated());
    assertNotEquals(original.paymentId(), corrected.paymentId());
    assertEquals("Anulado", finance.payment(original.paymentId()).status());
    assertEquals(original.paymentId(), finance.payment(corrected.paymentId()).originalPaymentId());
    assertEquals("Anulada", finance.invoice(original.invoiceId()).businessStatus());
    assertEquals(
        corrected.paymentId(),
        finance.correctPayment(original.paymentId(), replacement, "Retry").paymentId());
    assertEquals(
        money("20.00"),
        finance.overdue().stream()
            .filter(b -> b.obligationId() == obligation)
            .findFirst()
            .orElseThrow()
            .balance());
    assertThrows(
        BusinessException.class,
        () ->
            finance.correctPayment(
                corrected.paymentId(), request(obligation, guardian, "150.00"), "Invalid amount"));
    assertEquals("Confirmado", finance.payment(corrected.paymentId()).status());
    assertEquals("Emitida", finance.invoice(corrected.invoiceId()).businessStatus());
  }

  @Test
  void failedCancelledPdfRetainsReversalAndCanBeRegenerated() {
    var working = finance();
    long guardian = guardian(), student = student(guardian);
    long obligation = working.createObligation(student, 1, LocalDate.now(), unique());
    var payment = working.confirmPayment(request(obligation, guardian, "10.00"));
    var failing =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            invoice -> {
              throw new IOException("Disk failure");
            },
            SchoolInformation.fromEnvironment());
    assertFalse(failing.voidPayment(payment.paymentId(), "Reverse").pdfGenerated());
    assertEquals("Anulado", working.payment(payment.paymentId()).status());
    assertEquals("Fallida", working.invoice(payment.invoiceId()).status());
    assertTrue(working.regenerate(payment.invoiceId()).pdfGenerated());
    assertEquals("Anulada", working.invoice(payment.invoiceId()).businessStatus());
  }

  @Test
  void resourceAndBothLoanTypesHaveEditableAndCancellableHistory() {
    long category =
        catalogs.create(
            CatalogType.RESOURCE_CATEGORY, new CatalogEntry(0, "Test " + unique(), 0, true));
    long resource =
        resources.create(
            new SchoolResource(0, unique(), "Resource", category, "Library", "Disponible"));
    resources.updateResource(
        new SchoolResource(resource, unique(), "Edited", category, "Lab", "Disponible"));
    assertEquals("Edited", resources.resource(resource).name());
    for (BorrowerType type : BorrowerType.values()) {
      long borrower = type == BorrowerType.STUDENT ? student(guardian()) : teacher();
      long loan = resources.lend(resource, borrower, type, LocalDate.now().plusDays(1));
      resources.updateDueDate(loan, type, LocalDate.now().plusDays(3));
      assertEquals(LocalDate.now().plusDays(3), resources.loan(loan, type).due());
      assertThrows(BusinessException.class, () -> resources.deactivateResource(resource));
      resources.cancelLoan(loan, type, "Incorrect request");
      assertEquals("Anulado", resources.loan(loan, type).status());
      assertEquals("Disponible", resources.resource(resource).status());
      assertThrows(
          BusinessException.class,
          () -> resources.updateDueDate(loan, type, LocalDate.now().plusDays(4)));
    }
    resources.deactivateResource(resource);
    assertEquals("Dado de Baja", resources.resource(resource).status());
    assertEquals(
        2,
        Arrays.stream(BorrowerType.values())
            .flatMap(t -> resources.loans(t).stream())
            .filter(l -> l.resourceId() == resource)
            .count());
  }

  @Test
  void consoleEditingIsConnectedToRealServicesAndSupportsCancellation() {
    long guardian = guardian(), student = student(guardian);
    String input = "2\n" + student + "\n\nConsoleEdited\n\n\n\n1\n2\nq\n0\n";
    var bytes = new ByteArrayOutputStream();
    var view =
        new ConsoleView(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(bytes));
    new StudentAdministrationController(students, view).run();
    assertEquals("ConsoleEdited", students.student(student).firstName());
    String output = bytes.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("Estudiante actualizado"));
    assertTrue(output.contains("Operación cancelada"));
  }

  @Test
  void concurrentRestorationCannotExceedClubCapacity() throws Exception {
    long first = student(guardian()), second = student(guardian());
    long club = activities.createClub(new Club(0, "Test " + unique(), teacher(), 1));
    activities.join(club, first);
    long membership = activities.memberships(club).get(0).id();
    activities.withdraw(club, first);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (Runnable task :
          List.<Runnable>of(
              () -> activities.updateMembership(membership, LocalDate.now(), "Activa"),
              () -> activities.join(club, second)))
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    task.run();
                    return true;
                  } catch (BusinessException rejected) {
                    assertEquals("El club no tiene cupos.", rejected.getMessage());
                    return false;
                  }
                }));
      start.countDown();
      int successes = 0;
      for (var future : futures) if (future.get(20, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
      assertEquals(
          1,
          activities.memberships(club).stream().filter(m -> m.status().equals("Activa")).count());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void restoringAnAssignmentAlsoValidatesItsExistingSchedules() {
    long first = teacher(), second = teacher(), subject = subject();
    String group = unique().substring(0, 6);
    academics.assign(first, subject, 2026, group, 6, LocalTime.of(8, 0), LocalTime.of(9, 0));
    TeachingAssignment old =
        academics.assignments().stream()
            .filter(a -> a.teacherId() == first && a.subjectId() == subject)
            .findFirst()
            .orElseThrow();
    academics.deactivateAssignment(old.id());
    academics.assign(second, subject, 2026, group, 6, LocalTime.of(8, 0), LocalTime.of(9, 0));
    assertThrows(
        BusinessException.class,
        () ->
            academics.assign(
                first, subject, 2026, group, 6, LocalTime.of(10, 0), LocalTime.of(11, 0)));
    assertFalse(
        academics.assignments().stream()
            .filter(a -> a.id() == old.id())
            .findFirst()
            .orElseThrow()
            .active());
    ClassSchedule schedule =
        academics.schedules().stream()
            .filter(v -> v.assignmentId() == old.id())
            .findFirst()
            .orElseThrow();
    academics.updateSchedule(schedule.id(), 6, LocalTime.of(10, 0), LocalTime.of(11, 0));
    academics.updateAssignment(new TeachingAssignment(old.id(), first, subject, 2026, group, true));
    academics.deactivateSubject(subject);
    academics.deactivateAssignment(old.id());
    assertFalse(
        academics.assignments().stream()
            .filter(a -> a.id() == old.id())
            .findFirst()
            .orElseThrow()
            .active());
  }

  @Test
  void unlinkingGuardianDoesNotEraseTheirHistoricalPayments() {
    var finance = finance();
    long guardian = guardian(), student = student(guardian), other = guardian();
    long obligation = finance.createObligation(student, 1, LocalDate.now(), unique());
    var payment = finance.confirmPayment(request(obligation, guardian, "10.00"));
    students.saveLink(new GuardianLink(student, other, "Tutor", true, true, true));
    students.removeLink(student, guardian);
    assertTrue(
        finance.history(guardian, true).stream()
            .anyMatch(row -> row.startsWith(payment.paymentId() + " |")));
  }

  @Test
  void staleIssuedDocumentCannotBeMarkedGeneratedAfterCancellation() throws Exception {
    var finance = finance();
    long guardian = guardian(), student = student(guardian);
    long obligation = finance.createObligation(student, 1, LocalDate.now(), unique());
    var payment = finance.confirmPayment(request(obligation, guardian, "10.00"));
    finance.voidPayment(payment.paymentId(), "Cancel");
    try (Connection c = connections.open()) {
      c.setAutoCommit(false);
      assertFalse(
          new JdbcFinanceDao().markPdfGenerated(c, payment.invoiceId(), "stale.pdf", "Emitida"));
      c.rollback();
    }
    assertEquals("Anulada", finance.invoice(payment.invoiceId()).businessStatus());
  }

  private ConsoleView console(String input, ByteArrayOutputStream bytes) {
    return new ConsoleView(
        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), new PrintStream(bytes));
  }

  @Test
  void administrationScreensEditAllFourModulesAndCatalogs() {
    var bytes = new ByteArrayOutputStream();
    long teacher = teacher();
    new AcademicAdministrationController(
            academics,
            console(
                String.join(
                    "\n", "2", String.valueOf(teacher), "", "UITeacher", "", "", "", "1", "0", ""),
                bytes))
        .run();
    assertEquals("UITeacher", academics.teacher(teacher).firstName());
    long club = activities.createClub(new Club(0, "Test " + unique(), teacher, 3));
    new ActivityAdministrationController(
            activities,
            console(
                String.join("\n", "2", String.valueOf(club), "UIClub", "", "", "", "0", ""), bytes))
        .run();
    assertEquals("UIClub", activities.club(club).name());
    var finance = finance();
    long concept = finance.createConcept(unique(), "Test", money("100.00"), BigDecimal.ZERO);
    new FinanceAdministrationController(
            finance,
            console(
                String.join(
                    "\n", "2", String.valueOf(concept), "", "UIConcept", "", "", "", "0", ""),
                bytes))
        .run();
    assertEquals("UIConcept", finance.concept(concept).name());
    long resource =
        resources.create(new SchoolResource(0, unique(), "Test", 1, "Room", "Disponible"));
    new ResourceAdministrationController(
            resources,
            console(
                String.join("\n", "2", String.valueOf(resource), "", "UIResource", "", "", "0", ""),
                bytes))
        .run();
    assertEquals("UIResource", resources.resource(resource).name());
    long grade =
        catalogs.create(CatalogType.GRADE, new CatalogEntry(0, "Test " + unique(), 2, true));
    new CatalogController(
            catalogs,
            console(
                String.join("\n", "1", "4", String.valueOf(grade), "UIGrade", "", "", "0", ""),
                bytes))
        .run();
    assertEquals("UIGrade", catalogs.get(CatalogType.GRADE, grade).name());
  }

  @Test
  void reducingCapacityAndJoiningCannotProduceAnOverbookedClub() throws Exception {
    long first = student(guardian()), second = student(guardian()), teacher = teacher();
    long club = activities.createClub(new Club(0, "Test " + unique(), teacher, 2));
    activities.join(club, first);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      futures.add(
          executor.submit(
              () -> {
                try {
                  start.await();
                  activities.join(club, second);
                } catch (BusinessException expected) {
                  assertEquals("El club no tiene cupos.", expected.getMessage());
                } catch (InterruptedException e) {
                  throw new AssertionError(e);
                }
              }));
      futures.add(
          executor.submit(
              () -> {
                try {
                  start.await();
                  activities.updateClub(new Club(club, "Edited " + unique(), teacher, 1, true));
                } catch (BusinessException expected) {
                  assertEquals(
                      "El cupo no puede ser menor que la membresía activa.", expected.getMessage());
                } catch (InterruptedException e) {
                  throw new AssertionError(e);
                }
              }));
      start.countDown();
      for (var future : futures) future.get(20, TimeUnit.SECONDS);
      assertTrue(
          activities.memberships(club).stream().filter(m -> m.status().equals("Activa")).count()
              <= activities.club(club).capacity());
    } finally {
      executor.shutdownNow();
    }
  }
}
