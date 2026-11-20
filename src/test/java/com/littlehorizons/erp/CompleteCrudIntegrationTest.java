package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.unique;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.controller.*;
import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.BusinessException;
import com.littlehorizons.erp.view.ConsoleView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class CompleteCrudIntegrationTest extends CrudIntegrationFixture {
  private final AcademicService academics = new AcademicService(connections, new JdbcAcademicDao());
  private final ActivityService activities = new ActivityService(connections, new JdbcActivityDao());
  private final ResourceService resources = new ResourceService(connections, new JdbcResourceDao());
  private final CatalogService catalogs = new CatalogService(connections, new JdbcCatalogDao());

  private long subject() {
    return academics.registerSubject(new Subject(0, unique(), "Test subject", 1));
  }

  private record Course(long student, long teacher, long subject, String section) {}

  private Course course() {
    long student = student(guardian()), teacher = teacher(academics), subject = subject();
    String section = unique().substring(0, 6);
    students.enroll(student, 1, 2026);
    academics.assign(teacher, subject, 2026, section, 4, LocalTime.of(10, 0), LocalTime.of(11, 0));
    academics.subscribe(student, subject, 2026, AcademicPeriod.I, section);
    return new Course(student, teacher, subject, section);
  }

  @Test
  void gradeFormRetriesOnlyTheGradeAndPersistsTheValidValue() {
    Course course = course();
    var output = new ByteArrayOutputStream();
    new AcademicController(
            academics,
            console(
                String.join("\n", "7", Long.toString(course.student()), Long.toString(course.subject()),
                    Long.toString(course.teacher()), "2026", "1", "-0.1", "3.456", "4,5", "0", ""),
                output))
        .run();

    String transcript = output.toString(StandardCharsets.UTF_8);
    assertEquals(3, transcript.split("Nota 0.0–5.0", -1).length - 1);
    assertTrue(transcript.contains("Nota inválida"));
    assertTrue(transcript.contains("Nota guardada."));
    assertEquals(List.of(new BigDecimal("4.5")),
        academics.history(course.student()).stream().map(AcademicRecord::grade).toList());
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
    long teacher = teacher(academics), subject = subject();
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
                    assignment.id(), teacher(academics), assignment.subjectId(), 2026, c.section(), true)));
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
      long borrower = type == BorrowerType.STUDENT ? student(guardian()) : teacher(academics);
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
  void restoringAnAssignmentAlsoValidatesItsExistingSchedules() {
    long first = teacher(academics), second = teacher(academics), subject = subject();
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

  private ConsoleView console(String input, ByteArrayOutputStream bytes) {
    return new ConsoleView(
        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), new PrintStream(bytes));
  }

  @Test
  void administrationScreensEditAllFourModulesAndCatalogs() {
    var bytes = new ByteArrayOutputStream();
    long teacher = teacher(academics);
    new AcademicAdministrationController(
            academics,
            console(
                String.join(
                    "\n", "2", String.valueOf(teacher), "", "UITeacher", "", "", "", "1", "0", ""),
                bytes))
        .run();
    assertEquals("UITeacher", academics.teacher(teacher).firstName());
    String clubName = "UIClub " + unique();
    long club = activities.createClub(new Club(0, "Test " + unique(), teacher, 3));
    new ActivityAdministrationController(
            activities,
            console(
                String.join("\n", "2", String.valueOf(club), clubName, "", "", "", "", "0", ""),
                bytes))
        .run();
    assertEquals(clubName, activities.club(club).name());
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
    String gradeName = "UIGrade " + unique();
    long grade =
        catalogs.create(CatalogType.GRADE, new CatalogEntry(0, "Test " + unique(), 2, true));
    new CatalogController(
            catalogs,
            console(
                String.join("\n", "1", "4", String.valueOf(grade), gradeName, "", "", "0", ""),
                bytes))
        .run();
    assertEquals(gradeName, catalogs.get(CatalogType.GRADE, grade).name());
  }
}
