package com.littlehorizons.erp.service;

import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.ConnectionProvider;
import java.math.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Manages academic records and reports and exposes scheduling to the console controllers. */
public final class AcademicService extends TransactionalService {
  private final AcademicScheduling scheduling;

  private final AcademicDao dao;
  private final Clock clock;

  public AcademicService(ConnectionProvider connections, AcademicDao dao) {
    this(connections, dao, Clock.system(ZoneId.of("America/Bogota")));
  }

  public AcademicService(ConnectionProvider connections, AcademicDao dao, Clock clock) {
    super(connections);
    this.dao = dao;
    this.scheduling = new AcademicScheduling(connections, dao);
    this.clock = Objects.requireNonNull(clock);
  }

  public long registerTeacher(Teacher teacher) {
    require(teacher != null, "Datos obligatorios.");
    text(teacher.document(), "Documento", 20);
    text(teacher.firstName(), "Nombre", 100);
    text(teacher.lastName(), "Apellido", 100);
    textLength(teacher.email(), "Correo", 100);
    textLength(teacher.specialization(), "Especialización", 100);
    require(
        teacher.email() != null && teacher.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
        "Correo inválido.");
    return transaction(c -> dao.createTeacher(c, teacher));
  }

  public long registerSubject(Subject subject) {
    require(subject != null, "Datos obligatorios.");
    text(subject.code(), "Código", 20);
    text(subject.name(), "Nombre", 100);
    return transaction(
        c -> {
          require(dao.activeGrade(c, subject.gradeId()), "Grado inactivo o inexistente.");
          return dao.createSubject(c, subject);
        });
  }

  public List<Teacher> teachers() {
    return transaction(dao::teachers);
  }

  public List<Subject> subjects() {
    return transaction(dao::subjects);
  }

  public void assign(
      long teacher,
      long subject,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end) {
    scheduling.assign(teacher, subject, year, section, day, start, end);
  }

  public void subscribe(
      long student, long subject, int year, AcademicPeriod period, String section) {
    require(period != null && period != AcademicPeriod.FINAL, "Seleccione período I–IV.");
    text(section, "Grupo", 10);
    transaction(
        c -> {
          require(
              dao.canSubscribe(c, student, subject, year, section),
              "La materia y el grupo no corresponden a una matrícula activa.");
          dao.subscribe(c, student, subject, year, period, section);
          return null;
        });
  }

  public void recordGrade(AcademicRecord record) {
    require(record != null, "Datos obligatorios.");
    require(
        record.period() != null && record.period() != AcademicPeriod.FINAL,
        "La nota final se calcula a partir de I–IV.");
    validateGrade(record.grade());
    transaction(
        c -> {
          require(dao.lockStudent(c, record.studentId()), "Estudiante inexistente.");
          require(
              dao.canGrade(
                  c,
                  record.studentId(),
                  record.subjectId(),
                  record.teacherId(),
                  record.year(),
                  record.period()),
              "Profesor no asignado o estudiante no inscrito.");
          dao.saveGrade(c, record);
          // A previously closed subject must be recalculated after a period grade changes.
          dao.deleteGrade(
              c, record.studentId(), record.subjectId(), record.year(), AcademicPeriod.FINAL);
          return null;
        });
  }

  /** Applies the same DECIMAL precision in the console and in direct service calls. */
  public static void validateGrade(BigDecimal grade) {
    require(
        grade != null
            && grade.signum() >= 0
            && grade.compareTo(new BigDecimal("5.0")) <= 0
            && grade.stripTrailingZeros().scale() <= 1,
        "Nota inválida: use 0.0 a 5.0 con máximo un decimal.");
  }

  public void recordAttendance(
      long student,
      long subject,
      long teacher,
      int year,
      AcademicPeriod period,
      LocalDate date,
      AttendanceStatus status) {
    require(
        isDatabaseDate(date) && !date.isAfter(LocalDate.now()) && date.getYear() == year,
        "Fecha de asistencia inválida.");
    require(
        status != null && period != null && period != AcademicPeriod.FINAL,
        "Estado o período inválido.");
    transaction(
        c -> {
          require(dao.lockStudent(c, student), "Estudiante inexistente.");
          require(
              dao.canGrade(c, student, subject, teacher, year, period),
              "Profesor no asignado o estudiante no inscrito.");
          dao.attendance(c, student, subject, teacher, year, period, date, status);
          return null;
        });
  }

  public List<AcademicRecord> history(long student) {
    return transaction(c -> dao.history(c, student));
  }

  /** A partial average uses available periods and excludes the separately stored final grade. */
  public Optional<BigDecimal> subjectAverage(long student, long subject, int year) {
    return average(
        history(student).stream()
            .filter(
                r ->
                    r.subjectId() == subject
                        && r.year() == year
                        && r.period() != AcademicPeriod.FINAL)
            .map(AcademicRecord::grade)
            .toList());
  }

  public Optional<BigDecimal> periodAverage(long student, int year, AcademicPeriod period) {
    require(period != null && period != AcademicPeriod.FINAL, "Seleccione período I–IV.");
    return average(
        history(student).stream()
            .filter(
                r -> r.year() == year && r.period() == period && r.period() != AcademicPeriod.FINAL)
            .map(AcademicRecord::grade)
            .toList());
  }

  public Optional<BigDecimal> courseAverage(
      long subject, int year, AcademicPeriod period, String section) {
    require(period != null && period != AcademicPeriod.FINAL, "Seleccione período I–IV.");
    return Optional.ofNullable(
            transaction(c -> dao.courseAverage(c, subject, year, period, section)))
        .map(v -> v.setScale(1, RoundingMode.HALF_UP));
  }

  public static Optional<BigDecimal> average(List<BigDecimal> grades) {
    return grades.isEmpty()
        ? Optional.empty()
        : Optional.of(
            grades.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(grades.size()), 1, RoundingMode.HALF_UP));
  }

  /** Uses the school's calendar week, including both Monday and Sunday. */
  public List<String> currentWeekAbsences() {
    LocalDate start = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    return absences(start, start.plusDays(6), 3);
  }

  public List<String> absences(LocalDate start, LocalDate end, int threshold) {
    require(
        isDatabaseDate(start) && isDatabaseDate(end) && !start.isAfter(end) && threshold >= 0,
        "Rango o umbral inválido.");
    return transaction(c -> dao.absenceCounts(c, start, end)).stream()
        .filter(summary -> summary.absentClasses() > threshold)
        .map(
            summary ->
                "Estudiante %d: %d clases ausentes acumuladas"
                    .formatted(summary.studentId(), summary.absentClasses()))
        .toList();
  }

  public Teacher teacher(long id) {
    return transaction(
        c ->
            dao.teacher(c, id, false)
                .orElseThrow(
                    () ->
                        new com.littlehorizons.erp.util.BusinessException(
                            "Profesor inexistente.")));
  }

  public Subject subject(long id) {
    return transaction(
        c ->
            dao.subject(c, id, false)
                .orElseThrow(
                    () ->
                        new com.littlehorizons.erp.util.BusinessException("Materia inexistente.")));
  }

  public void updateTeacher(Teacher teacher) {
    require(teacher != null, "Profesor obligatorio.");
    text(teacher.document(), "Documento", 20);
    text(teacher.firstName(), "Nombre", 100);
    text(teacher.lastName(), "Apellido", 100);
    textLength(teacher.email(), "Correo", 100);
    textLength(teacher.specialization(), "Especialización", 100);
    require(
        teacher.email() != null && teacher.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
        "Correo inválido.");
    require(
        teacher.status() != null
            && Set.of("Activo", "Inactivo", "Licencia").contains(teacher.status()),
        "Estado inválido.");
    transaction(
        c -> {
          require(dao.teacher(c, teacher.id(), true).isPresent(), "Profesor inexistente.");
          dao.updateTeacher(c, teacher);
          return null;
        });
  }

  public void deactivateTeacher(long id) {
    Teacher t = teacher(id);
    updateTeacher(
        new Teacher(
            t.id(),
            t.document(),
            t.firstName(),
            t.lastName(),
            t.email(),
            t.specialization(),
            "Inactivo"));
  }

  public void updateSubject(Subject subject) {
    require(subject != null, "Materia obligatoria.");
    text(subject.code(), "Código", 20);
    text(subject.name(), "Nombre", 100);
    transaction(
        c -> {
          Subject old =
              dao.subject(c, subject.id(), true)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Materia inexistente."));
          require(
              subject.gradeId() == old.gradeId() || !dao.subjectHasHistory(c, subject.id()),
              "No se puede cambiar el grado de una materia con historial.");
          if (subject.active() || old.gradeId() != subject.gradeId())
            require(dao.activeGrade(c, subject.gradeId()), "Grado inactivo.");
          dao.updateSubject(c, subject);
          return null;
        });
  }

  public void deactivateSubject(long id) {
    Subject s = subject(id);
    updateSubject(new Subject(id, s.code(), s.name(), s.gradeId(), false));
  }

  public List<TeachingAssignment> assignments() {
    return scheduling.assignments();
  }

  public List<ClassSchedule> schedules() {
    return scheduling.schedules();
  }

  public void updateAssignment(TeachingAssignment entry) {
    scheduling.updateAssignment(entry);
  }

  public void deactivateAssignment(long id) {
    scheduling.deactivateAssignment(id);
  }

  public void updateSchedule(long id, int day, LocalTime start, LocalTime end) {
    scheduling.updateSchedule(id, day, start, end);
  }

  public void deleteSchedule(long id) {
    scheduling.deleteSchedule(id);
  }

  public List<Subscription> subscriptions(long student) {
    return transaction(c -> dao.subscriptions(c, student));
  }

  public void updateSubscription(long id, String section, String status) {
    text(section, "Grupo", 10);
    require(
        status != null && Set.of("Activa", "Retirada", "Completada").contains(status),
        "Estado inválido.");
    transaction(
        c -> {
          Subscription old =
              dao.subscription(c, id)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Inscripción inexistente."));
          require(
              section.equals(old.section()) || !dao.subscriptionHasHistory(c, old),
              "El grupo no cambia tras registrar notas o asistencia.");
          if (status.equals("Activa"))
            require(
                dao.canSubscribe(c, old.studentId(), old.subjectId(), old.year(), section),
                "Matrícula o asignación no disponible.");
          dao.updateSubscription(
              c,
              new Subscription(
                  id, old.studentId(), old.subjectId(), old.year(), old.period(), section, status));
          return null;
        });
  }

  public void withdrawSubscription(long id) {
    transaction(
        c -> {
          Subscription old =
              dao.subscription(c, id)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Inscripción inexistente."));
          dao.updateSubscription(
              c,
              new Subscription(
                  id,
                  old.studentId(),
                  old.subjectId(),
                  old.year(),
                  old.period(),
                  old.section(),
                  "Retirada"));
          return null;
        });
  }

  public List<AttendanceRecord> attendances(long student) {
    return transaction(c -> dao.attendances(c, student));
  }

  public void deleteAttendance(long id) {
    transaction(
        c -> {
          require(dao.deleteAttendance(c, id) == 1, "Asistencia inexistente.");
          return null;
        });
  }

  public void deleteGrade(long student, long subject, int year, AcademicPeriod period) {
    require(period != null && period != AcademicPeriod.FINAL, "Seleccione I–IV.");
    transaction(
        c -> {
          require(dao.lockStudent(c, student), "Estudiante inexistente.");
          require(dao.deleteGrade(c, student, subject, year, period) == 1, "Nota inexistente.");
          dao.deleteGrade(c, student, subject, year, AcademicPeriod.FINAL);
          return null;
        });
  }

  /** Stores a final grade only after all four periods have been recorded. */
  public BigDecimal closeSubject(long student, long subject, int year) {
    return transaction(
        c -> {
          require(dao.lockStudent(c, student), "Estudiante inexistente.");
          var records =
              dao.history(c, student).stream()
                  .filter(
                      r ->
                          r.subjectId() == subject
                              && r.year() == year
                              && r.period() != AcademicPeriod.FINAL)
                  .toList();
          require(records.size() == 4, "Se necesitan notas de los cuatro períodos.");
          BigDecimal result =
              average(records.stream().map(AcademicRecord::grade).toList()).orElseThrow();
          dao.saveGrade(
              c,
              new AcademicRecord(
                  student,
                  subject,
                  records.get(0).teacherId(),
                  year,
                  AcademicPeriod.FINAL,
                  result));
          return result;
        });
  }
}
