package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class BusinessBoundariesIntegrationTest {
  private static final int YEAR = 2024;
  private final StudentService students = new StudentService(CONNECTIONS, new JdbcStudentDao());
  private final AcademicService academics = new AcademicService(CONNECTIONS, new JdbcAcademicDao());
  private final ActivityService activities =
      new ActivityService(CONNECTIONS, new JdbcActivityDao());
  private final ResourceService resources = new ResourceService(CONNECTIONS, new JdbcResourceDao());

  @Test
  void fourZeroGradesAreDistinctFromMissingGradesAndFinalRequiresAllPeriods() {
    long student = student(1), subject = subject();
    String section = unique().substring(0, 8);
    students.enroll(student, 1, YEAR);
    academics.assign(1, subject, YEAR, section, 1, LocalTime.of(7, 0), LocalTime.of(8, 0));
    assertEquals(Optional.empty(), academics.subjectAverage(student, subject, YEAR));
    assertEquals(
        Optional.empty(), academics.courseAverage(subject, YEAR, AcademicPeriod.I, section));
    assertThrows(BusinessException.class, () -> academics.closeSubject(student, subject, YEAR));
    BigDecimal zero = new BigDecimal("0.0");
    for (AcademicPeriod period :
        List.of(AcademicPeriod.I, AcademicPeriod.II, AcademicPeriod.III, AcademicPeriod.IV)) {
      academics.subscribe(student, subject, YEAR, period, section);
      academics.recordGrade(new AcademicRecord(student, subject, 1, YEAR, period, zero));
      assertEquals(Optional.of(zero), academics.subjectAverage(student, subject, YEAR));
      assertEquals(Optional.of(zero), academics.periodAverage(student, YEAR, period));
      assertEquals(Optional.of(zero), academics.courseAverage(subject, YEAR, period, section));
      if (period != AcademicPeriod.IV) {
        assertThrows(BusinessException.class, () -> academics.closeSubject(student, subject, YEAR));
        assertTrue(
            academics.history(student).stream().noneMatch(r -> r.period() == AcademicPeriod.FINAL));
      }
    }
    assertEquals(zero, academics.closeSubject(student, subject, YEAR));
    assertEquals(
        1,
        academics.history(student).stream()
            .filter(r -> r.period() == AcademicPeriod.FINAL)
            .count());
  }

  @Test
  void adjacentClassesAreAcceptedAndAMinuteOfOverlapIsRejected() {
    long first = subject(), second = subject();
    String section = unique().substring(0, 8);
    LocalTime seven = LocalTime.of(7, 0), eight = LocalTime.of(8, 0), nine = LocalTime.of(9, 0);
    long teacher = teacher();
    academics.assign(teacher, first, YEAR, section, 1, seven, eight);
    assertThrows(
        BusinessException.class,
        () -> academics.assign(teacher, second, YEAR, section, 1, eight.minusMinutes(1), nine));
    academics.assign(teacher, second, YEAR, section, 1, eight, nine);
    var matching =
        academics.schedules().stream()
            .filter(h -> h.start().equals(eight) && h.end().equals(nine))
            .toList();
    assertFalse(matching.isEmpty());
    var fractional =
        assertThrows(
            BusinessException.class,
            () -> academics.assign(teacher, second, YEAR, section, 2, eight, eight.plusNanos(1)));
    assertNull(fractional.getCause());
  }

  @Test
  void absencesAreReportedOnlyAboveTheThreshold() {
    long first = student(1), second = student(1), subject = subject(), teacher = teacher();
    String section = unique().substring(0, 8);
    LocalDate from = LocalDate.of(YEAR, 2, 29);
    academics.assign(teacher, subject, YEAR, section, 1, LocalTime.of(7, 0), LocalTime.of(8, 0));
    for (long student : new long[] {first, second}) {
      students.enroll(student, 1, YEAR);
      academics.subscribe(student, subject, YEAR, AcademicPeriod.I, section);
      for (int day = 0; day < 4; day++) {
        AttendanceStatus status =
            student == first && day == 3 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
        academics.recordAttendance(
            student, subject, teacher, YEAR, AcademicPeriod.I, from.plusDays(day), status);
      }
    }
    List<String> report = academics.absences(from, from.plusDays(3), 3);
    assertFalse(report.stream().anyMatch(row -> row.startsWith("Estudiante " + first + ":")));
    assertTrue(report.contains("Estudiante " + second + ": 4 ausencias"));
  }

  @Test
  void guardiansAndStudentsCanHaveMultipleLinks() {
    long guardian =
        students.registerGuardian(new Guardian(0, unique(), "Guardian", "QA", "555", null));
    long first = student(guardian), second = student(guardian);
    students.saveLink(new GuardianLink(first, 1, "Tutor", false, true, true));
    assertEquals(
        List.of(1L, guardian),
        students.links(first).stream().map(GuardianLink::guardianId).sorted().toList());
    assertEquals(
        List.of(guardian), students.links(second).stream().map(GuardianLink::guardianId).toList());
    assertEquals(1, students.links(first).stream().filter(GuardianLink::primary).count());
  }

  @Test
  void clubsAndEventsAllowLargeDescriptionsDirectlyInMysqlSchema() throws Exception {
    String clubName = "Club " + unique();
    String eventName = "Event " + unique();
    LocalDate date = LocalDate.of(2024, 2, 29);
    long club = activities.createClub(new Club(0, clubName, 1, 2));
    long event = activities.createEvent(new SchoolEvent(0, eventName, date, "Cultural", true));
    String large = ("Descripción, \"comillas\"; \\漢\r\n 🚀\u0000").repeat(180);
    try (var c = CONNECTIONS.open()) {
      Jdbc.update(c, "UPDATE clubes SET descripcion=? WHERE id_club=?", large, club);
      Jdbc.update(c, "UPDATE eventos SET descripcion=? WHERE id_evento=?", large, event);
      assertEquals(
          large,
          Jdbc.query(c, "SELECT descripcion FROM clubes WHERE id_club=?", r -> r.getString(1), club)
              .get(0));
      assertEquals(
          large,
          Jdbc.query(
                  c,
                  "SELECT descripcion FROM eventos WHERE id_evento=?",
                  r -> r.getString(1),
                  event)
              .get(0));
    }
  }

  @Test
  void retiredMemberCanRejoinWithoutDuplicatingAnActiveMembership() {
    long club = activities.createClub(new Club(0, "Club " + unique(), 1, 2));
    activities.join(club, 1);
    assertThrows(BusinessException.class, () -> activities.join(club, 1));
    activities.withdraw(club, 1);
    activities.join(club, 1);
    assertEquals(
        List.of("Retirada", "Activa"),
        activities.memberships(club).stream().map(ClubMembership::status).toList());
  }

  @Test
  void competitivePodiumPlacesAreExclusiveAndParticipationCanBeShared() {
    long event =
        activities.createEvent(
            new SchoolEvent(0, "Competition " + unique(), LocalDate.now(), "Deportivo", true));
    activities.participate(event, 1, "1er Lugar");
    assertThrows(BusinessException.class, () -> activities.participate(event, 2, "1er Lugar"));
    assertThrows(BusinessException.class, () -> activities.participate(event, 2, "4to Lugar"));
    activities.participate(event, 2, "2do Lugar");
    activities.participate(event, 3, "3er Lugar");
    activities.participate(event, 4, "Participante");
    activities.participate(event, 5, "Participante");
    assertThrows(
        BusinessException.class,
        () -> activities.updateParticipation(new EventParticipation(event, 4, "3er Lugar")));
    activities.updateParticipation(new EventParticipation(event, 3, "3er Lugar"));
    assertEquals(
        List.of("1er Lugar", "2do Lugar", "3er Lugar", "Participante", "Participante"),
        activities.participants(event).stream().map(EventParticipation::position).toList());
  }

  @Test
  void blankCompetitionPositionsClearResultsWithoutReachingTheColumnLimit() {
    LocalDate date = LocalDate.of(2024, 2, 29);
    String name = "Competition " + unique();
    long event = activities.createEvent(new SchoolEvent(0, name, date, "Deportivo", true));
    activities.participate(event, 1, "1er Lugar");
    activities.updateParticipation(new EventParticipation(event, 1, " ".repeat(500)));
    activities.updateEvent(new SchoolEvent(event, name, date, "Deportivo", false));
    activities.participate(event, 2, "\t".repeat(500));
    assertEquals(2, activities.participants(event).size());
    assertTrue(activities.participants(event).stream().allMatch(p -> p.position() == null));
  }

  @ParameterizedTest
  @EnumSource(BorrowerType.class)
  void loanLifecycleRejectsUnavailableInventoryAndDuplicateReturns(BorrowerType type)
      throws Exception {
    long resource = resource();
    LocalDate today = LocalDate.now();
    for (String status : List.of("En Mantenimiento", "Dado de Baja")) {
      resources.changeStatus(resource, status);
      assertThrows(BusinessException.class, () -> resources.lend(resource, 1, type, today));
    }
    resources.changeStatus(resource, "Disponible");
    assertThrows(
        BusinessException.class, () -> resources.lend(resource, 1, type, today.minusDays(1)));
    long loan = resources.lend(resource, 1, type, today);
    assertEquals("Activo", resources.loan(loan, type).status());
    assertThrows(BusinessException.class, () -> resources.lend(resource, 2, type, today));
    resources.returnLoan(loan, type);
    assertThrows(BusinessException.class, () -> resources.returnLoan(loan, type));
    assertEquals("Disponible", resources.resource(resource).status());
    assertEquals("Devuelto", resources.loan(loan, type).status());

    long secondLoan = resources.lend(resource, 1, type, today);
    String reason = "Motivo de anulación 🚀 ".repeat(150);
    resources.cancelLoan(secondLoan, type, reason);
    assertEquals("Anulado", resources.loan(secondLoan, type).status());
    String table = type == BorrowerType.STUDENT ? "prestamos_estudiante" : "prestamos_profesor";
    try (var connection = CONNECTIONS.open()) {
      assertEquals(
          reason,
          Jdbc.query(
                  connection,
                  "SELECT observaciones FROM " + table + " WHERE id_prestamo=?",
                  row -> row.getString(1),
                  secondLoan)
              .get(0));
    }
  }

  @ParameterizedTest
  @EnumSource(BorrowerType.class)
  void anOpenLoanBecomesOverdueWhenItsDueDatePasses(BorrowerType type) throws Exception {
    long resource = resource();
    LocalDate today = LocalDate.now();
    long loan = resources.lend(resource, 1, type, today);
    // Set the dates of this fixture to simulate the passage of time without updating its status.
    String table = type == BorrowerType.STUDENT ? "prestamos_estudiante" : "prestamos_profesor";
    try (var connection = CONNECTIONS.open()) {
      Jdbc.update(
          connection,
          "UPDATE "
              + table
              + " SET fecha_prestamo=?, fecha_devolucion_esperada=? WHERE id_prestamo=?",
          today.minusDays(2),
          today.minusDays(1),
          loan);
    }
    assertTrue(
        resources.overdue().stream().anyMatch(l -> l.id() == loan && l.borrowerType() == type));
    assertEquals("Vencido", resources.loan(loan, type).status());
    assertEquals(
        "Vencido",
        resources.loans(type).stream()
            .filter(l -> l.id() == loan)
            .findFirst()
            .orElseThrow()
            .status());
    resources.updateDueDate(loan, type, today.plusDays(1));
    assertEquals("Activo", resources.loan(loan, type).status());
  }

  private long student(long guardian) {
    return students.register(
        new Student(0, unique(), "Student", "QA", LocalDate.of(2012, 2, 29), null, "Activo"),
        guardian,
        "Tutor");
  }

  private long subject() {
    return academics.registerSubject(new Subject(0, unique(), "Boundary subject", 1));
  }

  private long teacher() {
    return academics.registerTeacher(
        new Teacher(0, unique(), "Test", "Teacher", unique() + "@example.invalid", null));
  }

  private long resource() {
    return resources.create(new SchoolResource(0, unique(), "Resource", 1, "Room", "Disponible"));
  }
}
