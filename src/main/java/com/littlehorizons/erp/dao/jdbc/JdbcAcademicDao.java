package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.model.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Coordinates academic persistence without taking ownership of the service transaction. */
public final class JdbcAcademicDao implements AcademicDao {
  private final JdbcSubscriptionQueries subscriptionQueries = new JdbcSubscriptionQueries();

  private final JdbcScheduleQueries scheduleQueries = new JdbcScheduleQueries();

  public long createTeacher(Connection c, Teacher t) throws SQLException {
    return Jdbc.insert(
        c,
        """
INSERT INTO
profesores(numero_documento,primer_nombre,primer_apellido,correo_electronico,especializacion,fecha_contratacion)
 VALUES(?,?,?,?,?,CURRENT_DATE)
""",
        t.document(),
        t.firstName(),
        t.lastName(),
        t.email(),
        t.specialization());
  }

  public long createSubject(Connection c, Subject s) throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO materias(codigo_materia,nombre_materia,id_grado) VALUES(?,?,?)",
        s.code(),
        s.name(),
        s.gradeId());
  }

  public List<Teacher> teachers(Connection c) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM profesores ORDER BY id_profesor",
        r ->
            new Teacher(
                r.getLong("id_profesor"),
                r.getString("numero_documento"),
                r.getString("primer_nombre"),
                r.getString("primer_apellido"),
                r.getString("correo_electronico"),
                r.getString("especializacion"),
                r.getString("estado")));
  }

  public List<Subject> subjects(Connection c) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM materias ORDER BY id_materia",
        r ->
            new Subject(
                r.getLong("id_materia"),
                r.getString("codigo_materia"),
                r.getString("nombre_materia"),
                r.getLong("id_grado"),
                r.getBoolean("esta_activa")));
  }

  public Optional<Long> lockGrade(Connection c, long subject) throws SQLException {
    return scheduleQueries.lockGrade(c, subject);
  }

  public boolean lockTeacher(Connection c, long teacher) throws SQLException {
    return scheduleQueries.lockTeacher(c, teacher);
  }

  public boolean hasScheduleConflict(
      Connection c,
      long teacher,
      long grade,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end)
      throws SQLException {
    return scheduleQueries.hasScheduleConflict(c, teacher, grade, year, section, day, start, end);
  }

  public void assign(
      Connection c,
      long teacher,
      long subject,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end)
      throws SQLException {
    scheduleQueries.assign(c, teacher, subject, year, section, day, start, end);
  }

  public boolean canSubscribe(Connection c, long student, long subject, int year, String section)
      throws SQLException {
    return subscriptionQueries.canSubscribe(c, student, subject, year, section);
  }

  public void subscribe(
      Connection c, long student, long subject, int year, AcademicPeriod period, String section)
      throws SQLException {
    subscriptionQueries.subscribe(c, student, subject, year, period, section);
  }

  public boolean canGrade(
      Connection c, long student, long subject, long teacher, int year, AcademicPeriod period)
      throws SQLException {
    return Jdbc.count(
            c,
            """
SELECT COUNT(*) FROM inscripciones_materia i JOIN asignaciones_profesor a ON
a.id_materia=i.id_materia AND a.anio_academico=i.anio_academico AND a.seccion=i.seccion JOIN
profesores p ON p.id_profesor=a.id_profesor JOIN materias m ON m.id_materia=i.id_materia JOIN
matriculas mt ON mt.id_estudiante=i.id_estudiante AND mt.anio_academico=i.anio_academico AND
mt.id_grado=m.id_grado JOIN estudiantes e ON e.id_estudiante=i.id_estudiante WHERE i.id_estudiante=?
AND i.id_materia=? AND a.id_profesor=? AND i.anio_academico=? AND i.periodo_academico=? AND
i.estado='Activa' AND p.estado='Activo' AND mt.estado_matricula='Activa' AND e.estado='Activo' AND
m.esta_activa=TRUE AND a.esta_activa=TRUE
""",
            student,
            subject,
            teacher,
            year,
            period.databaseValue())
        > 0;
  }

  public void saveGrade(Connection c, AcademicRecord r) throws SQLException {
    Jdbc.update(
        c,
        """
INSERT INTO
registros_academicos(id_estudiante,id_materia,id_profesor,anio_academico,periodo_academico,valor_nota,fecha_registro)
 VALUES(?,?,?,?,?,?,CURRENT_DATE) ON DUPLICATE KEY UPDATE valor_nota=?,id_profesor=?
""",
        r.studentId(),
        r.subjectId(),
        r.teacherId(),
        r.year(),
        r.period().databaseValue(),
        r.grade(),
        r.grade(),
        r.teacherId());
  }

  public void attendance(
      Connection c,
      long student,
      long subject,
      long teacher,
      int year,
      AcademicPeriod period,
      LocalDate date,
      AttendanceStatus status)
      throws SQLException {
    Jdbc.update(
        c,
        """
INSERT INTO
asistencias(id_estudiante,id_materia,fecha_asistencia,estado_asistencia,registrado_por,anio_academico,periodo_academico)
 VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE
estado_asistencia=?,registrado_por=?,anio_academico=?,periodo_academico=?
""",
        student,
        subject,
        date,
        status.databaseValue(),
        teacher,
        year,
        period.databaseValue(),
        status.databaseValue(),
        teacher,
        year,
        period.databaseValue());
  }

  public List<AcademicRecord> history(Connection c, long student) throws SQLException {
    return Jdbc.query(
        c,
        """
        SELECT * FROM registros_academicos WHERE id_estudiante=? ORDER BY
        anio_academico,id_materia,periodo_academico
        """,
        r ->
            new AcademicRecord(
                r.getLong("id_estudiante"),
                r.getLong("id_materia"),
                r.getLong("id_profesor"),
                r.getInt("anio_academico"),
                "Final".equals(r.getString("periodo_academico"))
                    ? AcademicPeriod.FINAL
                    : AcademicPeriod.valueOf(r.getString("periodo_academico")),
                r.getBigDecimal("valor_nota")),
        student);
  }

  public List<String> absenceReport(Connection c, LocalDate start, LocalDate end, int threshold)
      throws SQLException {
    return absenceCounts(c, start, end).stream()
        .filter(summary -> summary.absentClasses() > threshold)
        .map(
            summary ->
                "Estudiante %d: %d ausencias"
                    .formatted(summary.studentId(), summary.absentClasses()))
        .toList();
  }

  public List<AbsenceSummary> absenceCounts(Connection c, LocalDate start, LocalDate end)
      throws SQLException {
    return Jdbc.query(
        c,
        """
        SELECT id_estudiante,COUNT(*) FROM asistencias
        WHERE fecha_asistencia BETWEEN ? AND ? AND estado_asistencia='Ausente'
        GROUP BY id_estudiante ORDER BY id_estudiante
        """,
        r -> new AbsenceSummary(r.getLong(1), r.getLong(2)),
        start,
        end);
  }

  public BigDecimal courseAverage(
      Connection c, long subject, int year, AcademicPeriod period, String section)
      throws SQLException {
    return Jdbc.query(
            c,
            """
SELECT AVG(r.valor_nota) FROM registros_academicos r JOIN inscripciones_materia i ON
i.id_estudiante=r.id_estudiante AND i.id_materia=r.id_materia AND i.anio_academico=r.anio_academico
AND i.periodo_academico=r.periodo_academico WHERE r.id_materia=? AND r.anio_academico=? AND
r.periodo_academico=? AND i.seccion=?
""",
            r -> r.getBigDecimal(1),
            subject,
            year,
            period.databaseValue(),
            section)
        .get(0);
  }

  public Optional<Teacher> teacher(Connection c, long id, boolean lock) throws SQLException {
    return Jdbc.query(
            c,
            lock
                ? "SELECT * FROM profesores WHERE id_profesor=? FOR UPDATE"
                : "SELECT * FROM profesores WHERE id_profesor=?",
            r ->
                new Teacher(
                    r.getLong("id_profesor"),
                    r.getString("numero_documento"),
                    r.getString("primer_nombre"),
                    r.getString("primer_apellido"),
                    r.getString("correo_electronico"),
                    r.getString("especializacion"),
                    r.getString("estado")),
            id)
        .stream()
        .findFirst();
  }

  public Optional<Subject> subject(Connection c, long id, boolean lock) throws SQLException {
    return Jdbc.query(
            c,
            lock
                ? "SELECT * FROM materias WHERE id_materia=? FOR UPDATE"
                : "SELECT * FROM materias WHERE id_materia=?",
            r ->
                new Subject(
                    r.getLong("id_materia"),
                    r.getString("codigo_materia"),
                    r.getString("nombre_materia"),
                    r.getLong("id_grado"),
                    r.getBoolean("esta_activa")),
            id)
        .stream()
        .findFirst();
  }

  public int updateTeacher(Connection c, Teacher t) throws SQLException {
    return Jdbc.update(
        c,
        """
UPDATE profesores SET
numero_documento=?,primer_nombre=?,primer_apellido=?,correo_electronico=?,especializacion=?,estado=?
WHERE id_profesor=?
""",
        t.document(),
        t.firstName(),
        t.lastName(),
        t.email(),
        t.specialization(),
        t.status(),
        t.id());
  }

  public int updateSubject(Connection c, Subject s) throws SQLException {
    return Jdbc.update(
        c,
        """
UPDATE materias SET codigo_materia=?,nombre_materia=?,id_grado=?,esta_activa=? WHERE id_materia=?
""",
        s.code(),
        s.name(),
        s.gradeId(),
        s.active(),
        s.id());
  }

  public boolean subjectHasHistory(Connection c, long id) throws SQLException {
    return Jdbc.count(
            c,
            """
SELECT EXISTS(SELECT 1 FROM inscripciones_materia WHERE id_materia=?) OR EXISTS(SELECT 1 FROM
asignaciones_profesor WHERE id_materia=?) OR EXISTS(SELECT 1 FROM registros_academicos WHERE
id_materia=?)
""",
            id,
            id,
            id)
        > 0;
  }

  public List<TeachingAssignment> assignments(Connection c) throws SQLException {
    return scheduleQueries.assignments(c);
  }

  public Optional<TeachingAssignment> assignment(Connection c, long id, boolean lock)
      throws SQLException {
    return scheduleQueries.assignment(c, id, lock);
  }

  public int updateAssignment(Connection c, TeachingAssignment a) throws SQLException {
    return scheduleQueries.updateAssignment(c, a);
  }

  public boolean assignmentHasHistory(Connection c, TeachingAssignment a) throws SQLException {
    return scheduleQueries.assignmentHasHistory(c, a);
  }

  public List<ClassSchedule> schedules(Connection c) throws SQLException {
    return scheduleQueries.schedules(c);
  }

  public Optional<ClassSchedule> schedule(Connection c, long id) throws SQLException {
    return scheduleQueries.schedule(c, id);
  }

  public int updateSchedule(Connection c, ClassSchedule s) throws SQLException {
    return scheduleQueries.updateSchedule(c, s);
  }

  public int deleteSchedule(Connection c, long id) throws SQLException {
    return scheduleQueries.deleteSchedule(c, id);
  }

  public boolean scheduleConflict(
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
    return scheduleQueries.scheduleConflict(c, teacher, grade, year, section, day, start, end, excluded);
  }

  public List<Subscription> subscriptions(Connection c, long student) throws SQLException {
    return subscriptionQueries.subscriptions(c, student);
  }

  public Optional<Subscription> subscription(Connection c, long id) throws SQLException {
    return subscriptionQueries.subscription(c, id);
  }

  public int updateSubscription(Connection c, Subscription s) throws SQLException {
    return subscriptionQueries.updateSubscription(c, s);
  }

  public boolean subscriptionHasHistory(Connection c, Subscription s) throws SQLException {
    return subscriptionQueries.subscriptionHasHistory(c, s);
  }

  public List<AttendanceRecord> attendances(Connection c, long student) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM asistencias WHERE id_estudiante=? ORDER BY fecha_asistencia,id_asistencia",
        r ->
            new AttendanceRecord(
                r.getLong("id_asistencia"),
                r.getLong("id_estudiante"),
                r.getLong("id_materia"),
                r.getLong("registrado_por"),
                r.getInt("anio_academico"),
                AcademicPeriod.valueOf(r.getString("periodo_academico")),
                r.getObject("fecha_asistencia", LocalDate.class),
                Arrays.stream(AttendanceStatus.values())
                    .filter(
                        v -> {
                          try {
                            return v.databaseValue().equals(r.getString("estado_asistencia"));
                          } catch (SQLException e) {
                            throw new IllegalStateException(e);
                          }
                        })
                    .findFirst()
                    .orElseThrow()),
        student);
  }

  public int deleteAttendance(Connection c, long id) throws SQLException {
    return Jdbc.update(c, "DELETE FROM asistencias WHERE id_asistencia=?", id);
  }

  public int deleteGrade(Connection c, long student, long subject, int year, AcademicPeriod period)
      throws SQLException {
    return Jdbc.update(
        c,
        """
DELETE FROM registros_academicos WHERE id_estudiante=? AND id_materia=? AND anio_academico=? AND
periodo_academico=?
""",
        student,
        subject,
        year,
        period.databaseValue());
  }

  public boolean activeGrade(Connection c, long grade) throws SQLException {
    return !Jdbc.query(
            c,
            "SELECT id_grado FROM grados WHERE id_grado=? AND esta_activo=TRUE FOR UPDATE",
            r -> r.getLong(1),
            grade)
        .isEmpty();
  }

  public boolean lockStudent(Connection c, long student) throws SQLException {
    return !Jdbc.query(
            c,
            "SELECT id_estudiante FROM estudiantes WHERE id_estudiante=? FOR UPDATE",
            r -> r.getLong(1),
            student)
        .isEmpty();
  }
}
