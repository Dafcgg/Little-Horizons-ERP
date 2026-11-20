package com.littlehorizons.erp.service;

import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.model.ClassSchedule;
import com.littlehorizons.erp.model.TeachingAssignment;
import com.littlehorizons.erp.util.ConnectionProvider;
import java.time.LocalTime;
import java.util.List;

/** Keeps schedule checks and assignment changes within the same transaction. */
final class AcademicScheduling extends TransactionalService {
  private final AcademicDao dao;

  AcademicScheduling(ConnectionProvider connections, AcademicDao dao) {
    super(connections);
    this.dao = dao;
  }

  void assign(
      long teacher,
      long subject,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end) {
    require(year >= 2000 && year <= 2100 && day >= 1 && day <= 7, "Año o día inválido.");
    text(section, "Grupo", 10);
    requireValidSchedule(day, start, end);
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
          // Reactivation makes every retained time slot visible again, including older ones.
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

  List<TeachingAssignment> assignments() {
    return transaction(dao::assignments);
  }

  List<ClassSchedule> schedules() {
    return transaction(dao::schedules);
  }

  void updateAssignment(TeachingAssignment entry) {
    require(entry != null, "Asignación obligatoria.");
    text(entry.section(), "Grupo", 10);
    require(entry.year() >= 2000 && entry.year() <= 2100, "Año inválido.");
    transaction(
        c -> {
          TeachingAssignment old =
              dao.assignment(c, entry.id(), true)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Asignación inexistente."));
          // Preserve the teaching group's identity once students have been enrolled in it.
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

  void deactivateAssignment(long id) {
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

  private static void requireValidSchedule(int day, LocalTime start, LocalTime end) {
    require(
        day >= 1 && day <= 7 && start != null && end != null && start.isBefore(end),
        "Horario inválido.");
    require(start.getNano() == 0 && end.getNano() == 0, "El horario admite hasta segundos.");
  }

  void updateSchedule(long id, int day, LocalTime start, LocalTime end) {
    requireValidSchedule(day, start, end);
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

  void deleteSchedule(long id) {
    transaction(
        c -> {
          require(dao.deleteSchedule(c, id) == 1, "Horario inexistente.");
          return null;
        });
  }
}
