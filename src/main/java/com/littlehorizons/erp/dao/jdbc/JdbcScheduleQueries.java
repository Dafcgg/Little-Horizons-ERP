package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.model.ClassSchedule;
import com.littlehorizons.erp.model.TeachingAssignment;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Stores teaching assignments and schedules using the caller's transaction. */
final class JdbcScheduleQueries {
  Optional<Long> lockGrade(Connection c, long subject) throws SQLException {
    return Jdbc.query(
            c,
            """
SELECT g.id_grado FROM grados g JOIN materias m ON m.id_grado=g.id_grado WHERE m.id_materia=? AND
m.esta_activa=TRUE AND g.esta_activo=TRUE FOR UPDATE
""",
            r -> r.getLong(1),
            subject)
        .stream()
        .findFirst();
  }

  boolean lockTeacher(Connection c, long teacher) throws SQLException {
    return !Jdbc.query(
            c,
            "SELECT id_profesor FROM profesores WHERE id_profesor=? AND estado='Activo' FOR UPDATE",
            r -> r.getLong(1),
            teacher)
        .isEmpty();
  }

  boolean hasScheduleConflict(
      Connection c,
      long teacher,
      long grade,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end)
      throws SQLException {
    // Strict overlap comparisons allow one class to start at the exact end of another.
    return Jdbc.count(
            c,
            """
SELECT COUNT(*) FROM horarios h JOIN asignaciones_profesor a ON a.id_asignacion=h.id_asignacion JOIN
materias m ON m.id_materia=a.id_materia WHERE a.esta_activa=TRUE AND a.anio_academico=? AND
h.dia_semana=? AND h.hora_inicio<? AND h.hora_fin>? AND (a.id_profesor=? OR (m.id_grado=? AND
a.seccion=? AND a.esta_activa=TRUE))
""",
            year,
            day,
            end,
            start,
            teacher,
            grade,
            section)
        > 0;
  }

  void assign(
      Connection c,
      long teacher,
      long subject,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end)
      throws SQLException {
    // Reuse the historical assignment key when a teaching group is reactivated.
    var assignments =
        Jdbc.query(
            c,
            """
SELECT id_asignacion FROM asignaciones_profesor WHERE id_profesor=? AND id_materia=? AND
anio_academico=? AND seccion=?
""",
            r -> r.getLong(1),
            teacher,
            subject,
            year,
            section);
    long assignment =
        assignments.isEmpty()
            ? Jdbc.insert(
                c,
                """
INSERT INTO asignaciones_profesor(id_profesor,id_materia,anio_academico,seccion) VALUES(?,?,?,?)
""",
                teacher,
                subject,
                year,
                section)
            : assignments.get(0);
    Jdbc.update(
        c, "UPDATE asignaciones_profesor SET esta_activa=TRUE WHERE id_asignacion=?", assignment);
    Jdbc.update(
        c,
        "INSERT INTO horarios(id_asignacion,dia_semana,hora_inicio,hora_fin) VALUES(?,?,?,?)",
        assignment,
        day,
        start,
        end);
  }

  private TeachingAssignment assignment(ResultSet r) throws SQLException {
    return new TeachingAssignment(
        r.getLong("id_asignacion"),
        r.getLong("id_profesor"),
        r.getLong("id_materia"),
        r.getInt("anio_academico"),
        r.getString("seccion"),
        r.getBoolean("esta_activa"));
  }

  List<TeachingAssignment> assignments(Connection c) throws SQLException {
    return Jdbc.query(
        c, "SELECT * FROM asignaciones_profesor ORDER BY id_asignacion", this::assignment);
  }

  Optional<TeachingAssignment> assignment(Connection c, long id, boolean lock)
      throws SQLException {
    return Jdbc.query(
            c,
            lock
                ? "SELECT * FROM asignaciones_profesor WHERE id_asignacion=? FOR UPDATE"
                : "SELECT * FROM asignaciones_profesor WHERE id_asignacion=?",
            this::assignment,
            id)
        .stream()
        .findFirst();
  }

  int updateAssignment(Connection c, TeachingAssignment a) throws SQLException {
    return Jdbc.update(
        c,
        """
UPDATE asignaciones_profesor SET id_profesor=?,id_materia=?,anio_academico=?,seccion=?,esta_activa=?
WHERE id_asignacion=?
""",
        a.teacherId(),
        a.subjectId(),
        a.year(),
        a.section(),
        a.active(),
        a.id());
  }

  boolean assignmentHasHistory(Connection c, TeachingAssignment a) throws SQLException {
    return Jdbc.count(
            c,
            """
SELECT COUNT(*) FROM inscripciones_materia WHERE id_materia=? AND anio_academico=? AND seccion=?
""",
            a.subjectId(),
            a.year(),
            a.section())
        > 0;
  }

  private ClassSchedule schedule(ResultSet r) throws SQLException {
    return new ClassSchedule(
        r.getLong("id_horario"),
        r.getLong("id_asignacion"),
        r.getInt("dia_semana"),
        r.getTime("hora_inicio").toLocalTime(),
        r.getTime("hora_fin").toLocalTime());
  }

  List<ClassSchedule> schedules(Connection c) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM horarios ORDER BY id_horario", this::schedule);
  }

  Optional<ClassSchedule> schedule(Connection c, long id) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM horarios WHERE id_horario=? FOR UPDATE", this::schedule, id)
        .stream()
        .findFirst();
  }

  int updateSchedule(Connection c, ClassSchedule s) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE horarios SET dia_semana=?,hora_inicio=?,hora_fin=? WHERE id_horario=?",
        s.day(),
        s.start(),
        s.end(),
        s.id());
  }

  int deleteSchedule(Connection c, long id) throws SQLException {
    return Jdbc.update(c, "DELETE FROM horarios WHERE id_horario=?", id);
  }

  boolean scheduleConflict(
      Connection c,
      long teacher,
      long grade,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end,
      long excluded)
      throws SQLException {
    return Jdbc.count(
            c,
            """
SELECT COUNT(*) FROM horarios h JOIN asignaciones_profesor a ON a.id_asignacion=h.id_asignacion JOIN
materias m ON m.id_materia=a.id_materia WHERE h.id_horario<>? AND a.esta_activa=TRUE AND
a.anio_academico=? AND h.dia_semana=? AND h.hora_inicio<? AND h.hora_fin>? AND (a.id_profesor=? OR
(m.id_grado=? AND a.seccion=?))
""",
            excluded,
            year,
            day,
            end,
            start,
            teacher,
            grade,
            section)
        > 0;
  }
}
