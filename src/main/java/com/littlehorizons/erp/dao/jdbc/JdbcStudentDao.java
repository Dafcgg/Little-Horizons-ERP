package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.model.*;
import java.sql.*;
import java.util.*;

public final class JdbcStudentDao implements StudentDao {
  public long createStudent(Connection c, Student s) throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO"
            + " estudiantes(numero_documento,primer_nombre,primer_apellido,fecha_nacimiento,direccion,genero,fecha_ingreso)"
            + " VALUES(?,?,?,?,?,'Otro',CURRENT_DATE)",
        s.document(),
        s.firstName(),
        s.lastName(),
        s.birthDate(),
        s.address());
  }

  public long createGuardian(Connection c, Guardian g) throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO"
            + " acudientes(numero_documento,primer_nombre,primer_apellido,telefono_principal,direccion,parentesco)"
            + " VALUES(?,?,?,?,?,'Tutor')",
        g.document(),
        g.firstName(),
        g.lastName(),
        g.phone(),
        g.address());
  }

  public void linkGuardian(Connection c, long student, long guardian, String relationship)
      throws SQLException {
    Jdbc.update(
        c,
        "INSERT INTO"
            + " estudiante_acudiente(id_estudiante,id_acudiente,parentesco,es_responsable_financiero,es_principal)"
            + " VALUES(?,?,?,TRUE,TRUE)",
        student,
        guardian,
        relationship);
  }

  public boolean isActive(Connection c, long student) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT estado FROM estudiantes WHERE id_estudiante=? FOR UPDATE",
            r -> r.getString(1),
            student)
        .stream()
        .anyMatch("Activo"::equals);
  }

  public boolean hasGuardian(Connection c, long student) throws SQLException {
    return Jdbc.count(c, "SELECT COUNT(*) FROM estudiante_acudiente WHERE id_estudiante=?", student)
        > 0;
  }

  public void enroll(Connection c, long student, long grade, int year) throws SQLException {
    Jdbc.update(
        c,
        "INSERT INTO matriculas(id_estudiante,id_grado,año_academico,fecha_matricula)"
            + " VALUES(?,?,?,CURRENT_DATE)",
        student,
        grade,
        year);
  }

  public List<Student> findStudents(Connection c) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM estudiantes ORDER BY primer_apellido,id_estudiante",
        r ->
            new Student(
                r.getLong("id_estudiante"),
                r.getString("numero_documento"),
                r.getString("primer_nombre"),
                r.getString("primer_apellido"),
                r.getDate("fecha_nacimiento").toLocalDate(),
                r.getString("direccion"),
                r.getString("estado")));
  }

  public List<Guardian> findGuardians(Connection c) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM acudientes ORDER BY id_acudiente",
        r ->
            new Guardian(
                r.getLong("id_acudiente"),
                r.getString("numero_documento"),
                r.getString("primer_nombre"),
                r.getString("primer_apellido"),
                r.getString("telefono_principal"),
                r.getString("direccion"),
                r.getBoolean("esta_activo")));
  }

  public Optional<Student> lockStudent(Connection c, long id) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM estudiantes WHERE id_estudiante=? FOR UPDATE",
            r ->
                new Student(
                    r.getLong("id_estudiante"),
                    r.getString("numero_documento"),
                    r.getString("primer_nombre"),
                    r.getString("primer_apellido"),
                    r.getDate("fecha_nacimiento").toLocalDate(),
                    r.getString("direccion"),
                    r.getString("estado")),
            id)
        .stream()
        .findFirst();
  }

  public Optional<Guardian> lockGuardian(Connection c, long id) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM acudientes WHERE id_acudiente=? FOR UPDATE",
            r ->
                new Guardian(
                    r.getLong("id_acudiente"),
                    r.getString("numero_documento"),
                    r.getString("primer_nombre"),
                    r.getString("primer_apellido"),
                    r.getString("telefono_principal"),
                    r.getString("direccion"),
                    r.getBoolean("esta_activo")),
            id)
        .stream()
        .findFirst();
  }

  public int updateStudent(Connection c, Student s) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE estudiantes SET"
            + " numero_documento=?,primer_nombre=?,primer_apellido=?,fecha_nacimiento=?,direccion=?,estado=?"
            + " WHERE id_estudiante=?",
        s.document(),
        s.firstName(),
        s.lastName(),
        s.birthDate(),
        s.address(),
        s.status(),
        s.id());
  }

  public int updateGuardian(Connection c, Guardian g) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE acudientes SET"
            + " numero_documento=?,primer_nombre=?,primer_apellido=?,telefono_principal=?,direccion=?,esta_activo=?"
            + " WHERE id_acudiente=?",
        g.document(),
        g.firstName(),
        g.lastName(),
        g.phone(),
        g.address(),
        g.active(),
        g.id());
  }

  public List<GuardianLink> links(Connection c, long student) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM estudiante_acudiente WHERE id_estudiante=?",
        r ->
            new GuardianLink(
                r.getLong("id_estudiante"),
                r.getLong("id_acudiente"),
                r.getString("parentesco"),
                r.getBoolean("es_principal"),
                r.getBoolean("es_contacto_emergencia"),
                r.getBoolean("es_responsable_financiero")),
        student);
  }

  public void saveLink(Connection c, GuardianLink l) throws SQLException {
    if (l.primary())
      Jdbc.update(
          c,
          "UPDATE estudiante_acudiente SET es_principal=FALSE WHERE id_estudiante=?",
          l.studentId());
    Jdbc.update(
        c,
        "INSERT INTO"
            + " estudiante_acudiente(id_estudiante,id_acudiente,parentesco,es_principal,es_contacto_emergencia,es_responsable_financiero)"
            + " VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " parentesco=?,es_principal=?,es_contacto_emergencia=?,es_responsable_financiero=?",
        l.studentId(),
        l.guardianId(),
        l.relationship(),
        l.primary(),
        l.emergency(),
        l.financial(),
        l.relationship(),
        l.primary(),
        l.emergency(),
        l.financial());
  }

  public int deleteLink(Connection c, long student, long guardian) throws SQLException {
    return Jdbc.update(
        c,
        "DELETE FROM estudiante_acudiente WHERE id_estudiante=? AND id_acudiente=?",
        student,
        guardian);
  }

  public int activeGuardians(Connection c, long student, long excluded) throws SQLException {
    return (int)
        Jdbc.count(
            c,
            "SELECT COUNT(*) FROM estudiante_acudiente ea JOIN acudientes a ON"
                + " a.id_acudiente=ea.id_acudiente WHERE ea.id_estudiante=? AND a.esta_activo=TRUE"
                + " AND a.id_acudiente<>?",
            student,
            excluded);
  }

  public List<Long> guardianStudents(Connection c, long guardian) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT id_estudiante FROM estudiante_acudiente WHERE id_acudiente=? ORDER BY"
            + " id_estudiante",
        r -> r.getLong(1),
        guardian);
  }

  public List<Enrollment> enrollments(Connection c, long student) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM matriculas WHERE id_estudiante=? ORDER BY año_academico",
        this::enrollment,
        student);
  }

  private Enrollment enrollment(java.sql.ResultSet r) throws SQLException {
    return new Enrollment(
        r.getLong("id_matricula"),
        r.getLong("id_estudiante"),
        r.getLong("id_grado"),
        r.getInt("año_academico"),
        r.getString("estado_matricula"));
  }

  public Optional<Enrollment> lockEnrollment(Connection c, long id) throws SQLException {
    return Jdbc.query(
            c, "SELECT * FROM matriculas WHERE id_matricula=? FOR UPDATE", this::enrollment, id)
        .stream()
        .findFirst();
  }

  public boolean hasSubscriptions(Connection c, long student, int year) throws SQLException {
    return Jdbc.count(
            c,
            "SELECT COUNT(*) FROM inscripciones_materia WHERE id_estudiante=? AND año_academico=?",
            student,
            year)
        > 0;
  }

  public int updateEnrollment(Connection c, Enrollment e) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE matriculas SET id_grado=?,estado_matricula=?,fecha_retiro=CASE WHEN ?='Retirada'"
            + " THEN CURRENT_DATE ELSE NULL END WHERE id_matricula=?",
        e.gradeId(),
        e.status(),
        e.status(),
        e.id());
  }

  public boolean activeGrade(Connection c, long grade) throws SQLException {
    return !Jdbc.query(
            c,
            "SELECT id_grado FROM grados WHERE id_grado=? AND esta_activo=TRUE FOR UPDATE",
            r -> r.getLong(1),
            grade)
        .isEmpty();
  }
}
