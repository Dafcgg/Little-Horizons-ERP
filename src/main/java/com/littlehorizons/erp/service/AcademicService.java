package com.littlehorizons.erp.service;

import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.ConnectionProvider;
import java.math.*;
import java.time.*;
import java.util.*;

public final class AcademicService extends TransactionalService {
  private final AcademicDao dao;

  public AcademicService(ConnectionProvider connections, AcademicDao dao) {
    super(connections);
    this.dao = dao;
  }

  public long registerTeacher(Teacher teacher) {
    require(teacher != null, "Datos obligatorios.");
    text(teacher.document(), "Documento");
    text(teacher.firstName(), "Nombre");
    text(teacher.lastName(), "Apellido");
    require(
        teacher.email() != null && teacher.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
        "Correo inválido.");
    return transaction(c -> dao.createTeacher(c, teacher));
  }

  public long registerSubject(Subject subject) {
    require(subject != null, "Datos obligatorios.");
    text(subject.code(), "Código");
    text(subject.name(), "Nombre");
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
    require(year >= 2000 && year <= 2100 && day >= 1 && day <= 7, "Año o día inválido.");
    text(section, "Grupo");
    require(start != null && end != null && start.isBefore(end), "Horario inválido.");
    transaction(
        c -> {
          // Lock in a stable order before checking competing assignments.
          long grade =
              dao.lockGrade(c, subject)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Materia no disponible."));
          require(dao.lockTeacher(c, teacher), "Profesor no disponible.");
          require(
              !dao.hasScheduleConflict(c, teacher, grade, year, section, day, start, end),
              "Cruce de horario del profesor o grupo.");
          var previous =
              dao.assignments(c).stream()
                  .filter(
                      a ->
                          a.teacherId() == teacher
                              && a.subjectId() == subject
                              && a.year() == year
                              && a.section().equals(section))
                  .findFirst();
          if (previous.isPresent() && !previous.get().active()) {
            for (var oldSchedule : dao.schedules(c))
              if (oldSchedule.assignmentId() == previous.get().id())
                require(
                    !dao.scheduleConflict(
                        c,
                        teacher,
                        grade,
                        year,
                        section,
                        oldSchedule.day(),
                        oldSchedule.start(),
                        oldSchedule.end(),
                        oldSchedule.id()),
                    "La reactivación causaría un cruce de horario existente.");
          }
          dao.assign(c, teacher, subject, year, section, day, start, end);
          return null;
        });
  }

  public void subscribe(
      long student, long subject, int year, AcademicPeriod period, String section) {
    require(period != null && period != AcademicPeriod.FINAL, "Seleccione período I–IV.");
    text(section, "Grupo");
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
          dao.deleteGrade(
              c, record.studentId(), record.subjectId(), record.year(), AcademicPeriod.FINAL);
          return null;
        });
  }

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
        date != null && !date.isAfter(LocalDate.now()) && date.getYear() == year,
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

  public List<String> absences(LocalDate start, LocalDate end, int threshold) {
    require(
        start != null && end != null && !start.isAfter(end) && threshold >= 0,
        "Rango o umbral inválido.");
    return transaction(c -> dao.absenceReport(c, start, end, threshold));
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
    text(teacher.document(), "Documento");
    text(teacher.firstName(), "Nombre");
    text(teacher.lastName(), "Apellido");
    require(
        teacher.email() != null && teacher.email().matches("[^ @]+@[^ @]+[.][^ @]+"),
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
    text(subject.code(), "Código");
    text(subject.name(), "Nombre");
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
    return transaction(dao::assignments);
  }

  public List<ClassSchedule> schedules() {
    return transaction(dao::schedules);
  }

  public void updateAssignment(TeachingAssignment entry) {
    require(entry != null, "Asignación obligatoria.");
    text(entry.section(), "Grupo");
    require(entry.year() >= 2000 && entry.year() <= 2100, "Año inválido.");
    transaction(
        c -> {
          TeachingAssignment old =
              dao.assignment(c, entry.id(), true)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Asignación inexistente."));
          boolean changed =
              old.teacherId() != entry.teacherId()
                  || old.subjectId() != entry.subjectId()
                  || old.year() != entry.year()
                  || !old.section().equals(entry.section());
          require(
              !changed || !dao.assignmentHasHistory(c, old),
              "La asignación con estudiantes inscritos conserva sus datos; inactívela y cree"
                  + " otra.");
          long grade =
              dao.lockGrade(c, entry.subjectId())
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Materia no disponible."));
          if (entry.active()) {
            require(dao.lockTeacher(c, entry.teacherId()), "Profesor inactivo.");
            for (ClassSchedule schedule : dao.schedules(c)) {
              if (schedule.assignmentId() == entry.id())
                require(
                    !dao.scheduleConflict(
                        c,
                        entry.teacherId(),
                        grade,
                        entry.year(),
                        entry.section(),
                        schedule.day(),
                        schedule.start(),
                        schedule.end(),
                        schedule.id()),
                    "Cruce de horario.");
            }
          }
          dao.updateAssignment(c, entry);
          return null;
        });
  }

  public void deactivateAssignment(long id) {
    transaction(
        c -> {
          TeachingAssignment a =
              dao.assignment(c, id, true)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Asignación inexistente."));
          dao.updateAssignment(
              c,
              new TeachingAssignment(
                  id, a.teacherId(), a.subjectId(), a.year(), a.section(), false));
          return null;
        });
  }

  public void updateSchedule(long id, int day, LocalTime start, LocalTime end) {
    require(
        day >= 1 && day <= 7 && start != null && end != null && start.isBefore(end),
        "Horario inválido.");
    transaction(
        c -> {
          ClassSchedule old =
              dao.schedule(c, id)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Horario inexistente."));
          TeachingAssignment a = dao.assignment(c, old.assignmentId(), true).orElseThrow();
          if (!a.active()) {
            dao.updateSchedule(c, new ClassSchedule(id, a.id(), day, start, end));
            return null;
          }
          long grade =
              dao.lockGrade(c, a.subjectId())
                  .orElseThrow(
                      () -> new com.littlehorizons.erp.util.BusinessException("Materia inactiva."));
          require(
              a.active() && dao.lockTeacher(c, a.teacherId()), "Asignación o profesor inactivo.");
          require(
              !dao.scheduleConflict(
                  c, a.teacherId(), grade, a.year(), a.section(), day, start, end, id),
              "Cruce de horario.");
          dao.updateSchedule(c, new ClassSchedule(id, a.id(), day, start, end));
          return null;
        });
  }

  public void deleteSchedule(long id) {
    transaction(
        c -> {
          require(dao.deleteSchedule(c, id) == 1, "Horario inexistente.");
          return null;
        });
  }

  public List<Subscription> subscriptions(long student) {
    return transaction(c -> dao.subscriptions(c, student));
  }

  public void updateSubscription(long id, String section, String status) {
    text(section, "Grupo");
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
