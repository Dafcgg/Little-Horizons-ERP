package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.AcademicDao;
import com.littlehorizons.erp.model.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class JdbcAcademicDao implements AcademicDao {
  public long createTeacher(Connection c, Teacher t) throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO"
            + " profesores(numero_documento,primer_nombre,primer_apellido,correo_electronico,especializacion,fecha_contratacion)"
            + " VALUES(?,?,?,?,?,CURRENT_DATE)",
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
    return Jdbc.query(
            c,
            "SELECT g.id_grado FROM grados g JOIN materias m ON m.id_grado=g.id_grado WHERE"
                + " m.id_materia=? AND m.esta_activa=TRUE AND g.esta_activo=TRUE FOR UPDATE",
            r -> r.getLong(1),
            subject)
        .stream()
        .findFirst();
  }

  public boolean lockTeacher(Connection c, long teacher) throws SQLException {
    return !Jdbc.query(
            c,
            "SELECT id_profesor FROM profesores WHERE id_profesor=? AND estado='Activo' FOR UPDATE",
            r -> r.getLong(1),
            teacher)
        .isEmpty();
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
    return Jdbc.count(
            c,
            "SELECT COUNT(*) FROM horarios h JOIN asignaciones_profesor a ON"
                + " a.id_asignacion=h.id_asignacion JOIN materias m ON m.id_materia=a.id_materia"
                + " WHERE a.esta_activa=TRUE AND a.año_academico=? AND h.dia_semana=? AND"
                + " h.hora_inicio<? AND h.hora_fin>? AND (a.id_profesor=? OR (m.id_grado=? AND"
                + " a.seccion=? AND a.esta_activa=TRUE))",
            year,
            day,
            end,
            start,
            teacher,
            grade,
            section)
        > 0;
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
    var assignments =
        Jdbc.query(
            c,
            "SELECT id_asignacion FROM asignaciones_profesor WHERE id_profesor=? AND id_materia=?"
                + " AND año_academico=? AND seccion=?",
            r -> r.getLong(1),
            teacher,
            subject,
            year,
            section);
    long assignment =
        assignments.isEmpty()
            ? Jdbc.insert(
                c,
                "INSERT INTO asignaciones_profesor(id_profesor,id_materia,año_academico,seccion)"
                    + " VALUES(?,?,?,?)",
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

  public boolean canSubscribe(Connection c, long student, long subject, int year, String section)
      throws SQLException {
    return Jdbc.count(
            c,
            "SELECT COUNT(*) FROM matriculas mt JOIN materias m ON m.id_grado=mt.id_grado JOIN"
                + " estudiantes e ON e.id_estudiante=mt.id_estudiante WHERE mt.id_estudiante=? AND"
                + " m.id_materia=? AND mt.año_academico=? AND mt.estado_matricula='Activa' AND"
                + " e.estado='Activo' AND m.esta_activa=TRUE AND EXISTS(SELECT 1 FROM"
                + " asignaciones_profesor a WHERE a.id_materia=m.id_materia AND"
                + " a.año_academico=mt.año_academico AND a.seccion=? AND a.esta_activa=TRUE)",
            student,
            subject,
            year,
            section)
        > 0;
  }

  public void subscribe(
      Connection c, long student, long subject, int year, AcademicPeriod period, String section)
      throws SQLException {
    Jdbc.update(
        c,
        "INSERT INTO"
            + " inscripciones_materia(id_estudiante,id_materia,año_academico,periodo_academico,seccion,fecha_inscripcion)"
            + " VALUES(?,?,?,?,?,CURRENT_DATE)",
        student,
        subject,
        year,
        period.databaseValue(),
        section);
  }

  public boolean canGrade(
      Connection c, long student, long subject, long teacher, int year, AcademicPeriod period)
      throws SQLException {
    return Jdbc.count(
            c,
            "SELECT COUNT(*) FROM inscripciones_materia i JOIN asignaciones_profesor a ON"
                + " a.id_materia=i.id_materia AND a.año_academico=i.año_academico AND"
                + " a.seccion=i.seccion JOIN profesores p ON p.id_profesor=a.id_profesor JOIN"
                + " materias m ON m.id_materia=i.id_materia JOIN matriculas mt ON"
                + " mt.id_estudiante=i.id_estudiante AND mt.año_academico=i.año_academico AND"
                + " mt.id_grado=m.id_grado JOIN estudiantes e ON e.id_estudiante=i.id_estudiante"
                + " WHERE i.id_estudiante=? AND i.id_materia=? AND a.id_profesor=? AND"
                + " i.año_academico=? AND i.periodo_academico=? AND i.estado='Activa' AND"
                + " p.estado='Activo' AND mt.estado_matricula='Activa' AND e.estado='Activo' AND"
                + " m.esta_activa=TRUE AND a.esta_activa=TRUE",
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
        "INSERT INTO"
            + " registros_academicos(id_estudiante,id_materia,id_profesor,año_academico,periodo_academico,valor_nota,fecha_registro)"
            + " VALUES(?,?,?,?,?,?,CURRENT_DATE) ON DUPLICATE KEY UPDATE"
            + " valor_nota=?,id_profesor=?",
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
        "INSERT INTO"
            + " asistencias(id_estudiante,id_materia,fecha_asistencia,estado_asistencia,registrado_por,año_academico,periodo_academico)"
            + " VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " estado_asistencia=?,registrado_por=?,año_academico=?,periodo_academico=?",
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
        "SELECT * FROM registros_academicos WHERE id_estudiante=? ORDER BY"
            + " año_academico,id_materia,periodo_academico",
        r ->
            new AcademicRecord(
                r.getLong("id_estudiante"),
                r.getLong("id_materia"),
                r.getLong("id_profesor"),
                r.getInt("año_academico"),
                "Final".equals(r.getString("periodo_academico"))
                    ? AcademicPeriod.FINAL
                    : AcademicPeriod.valueOf(r.getString("periodo_academico")),
                r.getBigDecimal("valor_nota")),
        student);
  }

  public List<String> absenceReport(Connection c, LocalDate start, LocalDate end, int threshold)
      throws SQLException {
    return Jdbc.query(
        c,
        "SELECT id_estudiante,COUNT(*) FROM asistencias WHERE fecha_asistencia BETWEEN ? AND ? AND"
            + " estado_asistencia='Ausente' GROUP BY id_estudiante HAVING COUNT(*)>?",
        r -> "Estudiante " + r.getLong(1) + ": " + r.getLong(2) + " ausencias",
        start,
        end,
        threshold);
  }

  public BigDecimal courseAverage(
      Connection c, long subject, int year, AcademicPeriod period, String section)
      throws SQLException {
    return Jdbc.query(
            c,
            "SELECT AVG(r.valor_nota) FROM registros_academicos r JOIN inscripciones_materia i ON"
                + " i.id_estudiante=r.id_estudiante AND i.id_materia=r.id_materia AND"
                + " i.año_academico=r.año_academico AND i.periodo_academico=r.periodo_academico"
                + " WHERE r.id_materia=? AND r.año_academico=? AND r.periodo_academico=? AND"
                + " i.seccion=?",
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
            "SELECT * FROM profesores WHERE id_profesor=?" + (lock ? " FOR UPDATE" : ""),
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
            "SELECT * FROM materias WHERE id_materia=?" + (lock ? " FOR UPDATE" : ""),
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
        "UPDATE profesores SET"
            + " numero_documento=?,primer_nombre=?,primer_apellido=?,correo_electronico=?,especializacion=?,estado=?"
            + " WHERE id_profesor=?",
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
        "UPDATE materias SET codigo_materia=?,nombre_materia=?,id_grado=?,esta_activa=? WHERE"
            + " id_materia=?",
        s.code(),
        s.name(),
        s.gradeId(),
        s.active(),
        s.id());
  }

  public boolean subjectHasHistory(Connection c, long id) throws SQLException {
    return Jdbc.count(
            c,
            "SELECT (SELECT COUNT(*) FROM inscripciones_materia WHERE id_materia=?)+(SELECT"
                + " COUNT(*) FROM asignaciones_profesor WHERE id_materia=?)+(SELECT COUNT(*) FROM"
                + " registros_academicos WHERE id_materia=?)",
            id,
            id,
            id)
        > 0;
  }

  private TeachingAssignment assignment(java.sql.ResultSet r) throws SQLException {
    return new TeachingAssignment(
        r.getLong("id_asignacion"),
        r.getLong("id_profesor"),
        r.getLong("id_materia"),
        r.getInt("año_academico"),
        r.getString("seccion"),
        r.getBoolean("esta_activa"));
  }

  public List<TeachingAssignment> assignments(Connection c) throws SQLException {
    return Jdbc.query(
        c, "SELECT * FROM asignaciones_profesor ORDER BY id_asignacion", this::assignment);
  }

  public Optional<TeachingAssignment> assignment(Connection c, long id, boolean lock)
      throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM asignaciones_profesor WHERE id_asignacion=?"
                + (lock ? " FOR UPDATE" : ""),
            this::assignment,
            id)
        .stream()
        .findFirst();
  }

  public int updateAssignment(Connection c, TeachingAssignment a) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE asignaciones_profesor SET"
            + " id_profesor=?,id_materia=?,año_academico=?,seccion=?,esta_activa=? WHERE"
            + " id_asignacion=?",
        a.teacherId(),
        a.subjectId(),
        a.year(),
        a.section(),
        a.active(),
        a.id());
  }

  public boolean assignmentHasHistory(Connection c, TeachingAssignment a) throws SQLException {
    return Jdbc.count(
            c,
            "SELECT COUNT(*) FROM inscripciones_materia WHERE id_materia=? AND año_academico=? AND"
                + " seccion=?",
            a.subjectId(),
            a.year(),
            a.section())
        > 0;
  }

  private ClassSchedule schedule(java.sql.ResultSet r) throws SQLException {
    return new ClassSchedule(
        r.getLong("id_horario"),
        r.getLong("id_asignacion"),
        r.getInt("dia_semana"),
        r.getTime("hora_inicio").toLocalTime(),
        r.getTime("hora_fin").toLocalTime());
  }

  public List<ClassSchedule> schedules(Connection c) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM horarios ORDER BY id_horario", this::schedule);
  }

  public Optional<ClassSchedule> schedule(Connection c, long id) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM horarios WHERE id_horario=? FOR UPDATE", this::schedule, id)
        .stream()
        .findFirst();
  }

  public int updateSchedule(Connection c, ClassSchedule s) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE horarios SET dia_semana=?,hora_inicio=?,hora_fin=? WHERE id_horario=?",
        s.day(),
        s.start(),
        s.end(),
        s.id());
  }

  public int deleteSchedule(Connection c, long id) throws SQLException {
    return Jdbc.update(c, "DELETE FROM horarios WHERE id_horario=?", id);
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
    return Jdbc.count(
            c,
            "SELECT COUNT(*) FROM horarios h JOIN asignaciones_profesor a ON"
                + " a.id_asignacion=h.id_asignacion JOIN materias m ON m.id_materia=a.id_materia"
                + " WHERE h.id_horario<>? AND a.esta_activa=TRUE AND a.año_academico=? AND"
                + " h.dia_semana=? AND h.hora_inicio<? AND h.hora_fin>? AND (a.id_profesor=? OR"
                + " (m.id_grado=? AND a.seccion=?))",
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

  private Subscription subscription(java.sql.ResultSet r) throws SQLException {
    return new Subscription(
        r.getLong("id_inscripcion"),
        r.getLong("id_estudiante"),
        r.getLong("id_materia"),
        r.getInt("año_academico"),
        AcademicPeriod.valueOf(r.getString("periodo_academico")),
        r.getString("seccion"),
        r.getString("estado"));
  }

  public List<Subscription> subscriptions(Connection c, long student) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM inscripciones_materia WHERE id_estudiante=? ORDER BY id_inscripcion",
        this::subscription,
        student);
  }

  public Optional<Subscription> subscription(Connection c, long id) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM inscripciones_materia WHERE id_inscripcion=? FOR UPDATE",
            this::subscription,
            id)
        .stream()
        .findFirst();
  }

  public int updateSubscription(Connection c, Subscription s) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE inscripciones_materia SET seccion=?,estado=? WHERE id_inscripcion=?",
        s.section(),
        s.status(),
        s.id());
  }

  public boolean subscriptionHasHistory(Connection c, Subscription s) throws SQLException {
    return Jdbc.count(
            c,
            "SELECT (SELECT COUNT(*) FROM registros_academicos WHERE id_estudiante=? AND"
                + " id_materia=? AND año_academico=? AND periodo_academico=?)+(SELECT COUNT(*) FROM"
                + " asistencias WHERE id_estudiante=? AND id_materia=? AND año_academico=? AND"
                + " periodo_academico=?)",
            s.studentId(),
            s.subjectId(),
            s.year(),
            s.period().databaseValue(),
            s.studentId(),
            s.subjectId(),
            s.year(),
            s.period().databaseValue())
        > 0;
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
                r.getInt("año_academico"),
                AcademicPeriod.valueOf(r.getString("periodo_academico")),
                r.getDate("fecha_asistencia").toLocalDate(),
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
        "DELETE FROM registros_academicos WHERE id_estudiante=? AND id_materia=? AND"
            + " año_academico=? AND periodo_academico=?",
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
