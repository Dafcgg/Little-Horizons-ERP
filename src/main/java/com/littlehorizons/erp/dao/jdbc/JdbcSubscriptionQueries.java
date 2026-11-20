package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.Subscription;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Persists enrollments in subjects and checks their academic history. */
final class JdbcSubscriptionQueries {
  boolean canSubscribe(Connection c, long student, long subject, int year, String section)
      throws SQLException {
    return Jdbc.count(
            c,
            """
SELECT COUNT(*) FROM matriculas mt JOIN materias m ON m.id_grado=mt.id_grado JOIN estudiantes e ON
e.id_estudiante=mt.id_estudiante WHERE mt.id_estudiante=? AND m.id_materia=? AND mt.anio_academico=?
AND mt.estado_matricula='Activa' AND e.estado='Activo' AND m.esta_activa=TRUE AND EXISTS(SELECT 1
FROM asignaciones_profesor a WHERE a.id_materia=m.id_materia AND a.anio_academico=mt.anio_academico
AND a.seccion=? AND a.esta_activa=TRUE)
""",
            student,
            subject,
            year,
            section)
        > 0;
  }

  void subscribe(
      Connection c, long student, long subject, int year, AcademicPeriod period, String section)
      throws SQLException {
    Jdbc.update(
        c,
        """
INSERT INTO
inscripciones_materia(id_estudiante,id_materia,anio_academico,periodo_academico,seccion,fecha_inscripcion)
 VALUES(?,?,?,?,?,CURRENT_DATE)
""",
        student,
        subject,
        year,
        period.databaseValue(),
        section);
  }

  private Subscription subscription(ResultSet r) throws SQLException {
    return new Subscription(
        r.getLong("id_inscripcion"),
        r.getLong("id_estudiante"),
        r.getLong("id_materia"),
        r.getInt("anio_academico"),
        AcademicPeriod.valueOf(r.getString("periodo_academico")),
        r.getString("seccion"),
        r.getString("estado"));
  }

  List<Subscription> subscriptions(Connection c, long student) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM inscripciones_materia WHERE id_estudiante=? ORDER BY id_inscripcion",
        this::subscription,
        student);
  }

  Optional<Subscription> subscription(Connection c, long id) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM inscripciones_materia WHERE id_inscripcion=? FOR UPDATE",
            this::subscription,
            id)
        .stream()
        .findFirst();
  }

  int updateSubscription(Connection c, Subscription s) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE inscripciones_materia SET seccion=?,estado=? WHERE id_inscripcion=?",
        s.section(),
        s.status(),
        s.id());
  }

  boolean subscriptionHasHistory(Connection c, Subscription s) throws SQLException {
    // Grades and attendance can both make a subscription's original group part of the record.
    return Jdbc.count(
            c,
            """
SELECT EXISTS(SELECT 1 FROM registros_academicos WHERE id_estudiante=? AND id_materia=? AND
anio_academico=? AND periodo_academico=?) OR EXISTS(SELECT 1 FROM asistencias WHERE id_estudiante=? AND
id_materia=? AND anio_academico=? AND periodo_academico=?)
""",
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
}
