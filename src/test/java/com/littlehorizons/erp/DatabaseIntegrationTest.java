package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.unique;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.BusinessException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class DatabaseIntegrationTest extends DatabaseIntegrationFixture {
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
}
