package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.ResourceDao;
import com.littlehorizons.erp.model.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public final class JdbcResourceDao implements ResourceDao {
  private SchoolResource resource(ResultSet r) throws SQLException {
    return new SchoolResource(
        r.getLong("id_recurso"),
        r.getString("codigo_recurso"),
        r.getString("nombre_recurso"),
        r.getLong("id_categoria"),
        r.getString("ubicacion_fisica"),
        r.getString("estado_recurso"));
  }

  public long create(Connection c, SchoolResource r) throws SQLException {
    return Jdbc.insert(
        c,
        """
        INSERT INTO recursos(codigo_recurso,nombre_recurso,id_categoria,ubicacion_fisica)
        VALUES(?,?,?,?)
        """,
        r.code(),
        r.name(),
        r.categoryId(),
        r.location());
  }

  public List<SchoolResource> resources(Connection c) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM recursos ORDER BY id_recurso", this::resource);
  }

  public Optional<SchoolResource> lock(Connection c, long id) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM recursos WHERE id_recurso=? FOR UPDATE", this::resource, id)
        .stream()
        .findFirst();
  }

  public void setStatus(Connection c, long id, String status) throws SQLException {
    Jdbc.update(c, "UPDATE recursos SET estado_recurso=? WHERE id_recurso=?", status, id);
  }

  private String borrower(BorrowerType type) {
    return type == BorrowerType.STUDENT ? "id_estudiante" : "id_profesor";
  }

  public long createLoan(
      Connection c, long resource, long borrower, BorrowerType type, LocalDate date, LocalDate due)
      throws SQLException {
    String sql =
        switch (type) {
          case STUDENT ->
              """
              INSERT INTO prestamos_estudiante
              (id_recurso,id_estudiante,fecha_prestamo,fecha_devolucion_esperada) VALUES(?,?,?,?)
              """;
          case TEACHER ->
              """
              INSERT INTO prestamos_profesor
              (id_recurso,id_profesor,fecha_prestamo,fecha_devolucion_esperada) VALUES(?,?,?,?)
              """;
        };
    return Jdbc.insert(c, sql, resource, borrower, date, due);
  }

  private Loan loan(ResultSet r, BorrowerType type) throws SQLException {
    return new Loan(
        r.getLong("id_prestamo"),
        r.getLong("id_recurso"),
        r.getLong(borrower(type)),
        type,
        r.getObject("fecha_devolucion_esperada", LocalDate.class));
  }

  public Optional<Loan> findLoan(Connection c, long id, BorrowerType type) throws SQLException {
    String sql =
        switch (type) {
          case STUDENT ->
              """
              SELECT * FROM prestamos_estudiante
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              AND estado_prestamo IN ('Activo','Vencido')
              """;
          case TEACHER ->
              """
              SELECT * FROM prestamos_profesor
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              AND estado_prestamo IN ('Activo','Vencido')
              """;
        };
    return Jdbc.query(c, sql, r -> loan(r, type), id).stream().findFirst();
  }

  public Optional<Loan> lockLoan(Connection c, long id, BorrowerType type) throws SQLException {
    String sql =
        switch (type) {
          case STUDENT ->
              """
              SELECT * FROM prestamos_estudiante
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              AND estado_prestamo IN ('Activo','Vencido') FOR UPDATE
              """;
          case TEACHER ->
              """
              SELECT * FROM prestamos_profesor
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              AND estado_prestamo IN ('Activo','Vencido') FOR UPDATE
              """;
        };
    return Jdbc.query(c, sql, r -> loan(r, type), id).stream().findFirst();
  }

  public void returnLoan(Connection c, long id, BorrowerType type, LocalDate date)
      throws SQLException {
    String sql =
        switch (type) {
          case STUDENT ->
              """
              UPDATE prestamos_estudiante SET estado_prestamo='Devuelto',fecha_devolucion_real=?
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              AND estado_prestamo IN ('Activo','Vencido')
              """;
          case TEACHER ->
              """
              UPDATE prestamos_profesor SET estado_prestamo='Devuelto',fecha_devolucion_real=?
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              AND estado_prestamo IN ('Activo','Vencido')
              """;
        };
    Jdbc.update(c, sql, date, id);
  }

  public List<Loan> overdue(Connection c, LocalDate date) throws SQLException {
    List<Loan> rows = new ArrayList<>();
    for (BorrowerType type : BorrowerType.values()) {
      String sql =
          switch (type) {
            case STUDENT ->
                """
                SELECT * FROM prestamos_estudiante
                WHERE fecha_devolucion_real IS NULL AND fecha_devolucion_esperada<?
                AND estado_prestamo IN ('Activo','Vencido')
                """;
            case TEACHER ->
                """
                SELECT * FROM prestamos_profesor
                WHERE fecha_devolucion_real IS NULL AND fecha_devolucion_esperada<?
                AND estado_prestamo IN ('Activo','Vencido')
                """;
          };
      rows.addAll(Jdbc.query(c, sql, r -> loan(r, type), date));
    }
    return rows;
  }

  public int updateResource(Connection c, SchoolResource r) throws SQLException {
    return Jdbc.update(
        c,
        """
        UPDATE recursos SET codigo_recurso=?,nombre_recurso=?,id_categoria=?,ubicacion_fisica=?
        WHERE id_recurso=?
        """,
        r.code(),
        r.name(),
        r.categoryId(),
        r.location(),
        r.id());
  }

  public boolean activeCategory(Connection c, long category) throws SQLException {
    return !Jdbc.query(
            c,
            """
            SELECT id_categoria FROM categorias_recurso
            WHERE id_categoria=? AND esta_activo=TRUE FOR UPDATE
            """,
            r -> r.getLong(1),
            category)
        .isEmpty();
  }

  public boolean activeBorrower(Connection c, long id, BorrowerType type) throws SQLException {
    String sql =
        switch (type) {
          case STUDENT ->
              """
              SELECT id_estudiante FROM estudiantes
              WHERE id_estudiante=? AND estado='Activo' FOR UPDATE
              """;
          case TEACHER ->
              """
              SELECT id_profesor FROM profesores
              WHERE id_profesor=? AND estado='Activo' FOR UPDATE
              """;
        };
    return !Jdbc.query(c, sql, r -> r.getLong(1), id).isEmpty();
  }

  private LoanEntry entry(ResultSet r, BorrowerType type) throws SQLException {
    LocalDate returned = r.getObject("fecha_devolucion_real", LocalDate.class);
    LocalDate due = r.getObject("fecha_devolucion_esperada", LocalDate.class);
    String status = r.getString("estado_prestamo");
    if ("Activo".equals(status) && due.isBefore(LocalDate.now())) status = "Vencido";
    return new LoanEntry(
        r.getLong("id_prestamo"),
        r.getLong("id_recurso"),
        r.getLong(borrower(type)),
        type,
        r.getObject("fecha_prestamo", LocalDate.class),
        due,
        returned,
        status);
  }

  public List<LoanEntry> loans(Connection c, BorrowerType type) throws SQLException {
    String sql =
        switch (type) {
          case STUDENT -> "SELECT * FROM prestamos_estudiante ORDER BY id_prestamo";
          case TEACHER -> "SELECT * FROM prestamos_profesor ORDER BY id_prestamo";
        };
    return Jdbc.query(c, sql, r -> entry(r, type));
  }

  public Optional<LoanEntry> loanEntry(Connection c, long id, BorrowerType type, boolean lock)
      throws SQLException {
    String sql;
    if (lock) {
      sql =
          switch (type) {
            case STUDENT -> "SELECT * FROM prestamos_estudiante WHERE id_prestamo=? FOR UPDATE";
            case TEACHER -> "SELECT * FROM prestamos_profesor WHERE id_prestamo=? FOR UPDATE";
          };
    } else {
      sql =
          switch (type) {
            case STUDENT -> "SELECT * FROM prestamos_estudiante WHERE id_prestamo=?";
            case TEACHER -> "SELECT * FROM prestamos_profesor WHERE id_prestamo=?";
          };
    }
    return Jdbc.query(c, sql, r -> entry(r, type), id).stream().findFirst();
  }

  public int updateDueDate(Connection c, long id, BorrowerType type, LocalDate due)
      throws SQLException {
    String sql =
        switch (type) {
          case STUDENT ->
              """
              UPDATE prestamos_estudiante
              SET fecha_devolucion_esperada=?,estado_prestamo=CASE WHEN ?<CURRENT_DATE
              THEN 'Vencido' ELSE 'Activo' END
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              """;
          case TEACHER ->
              """
              UPDATE prestamos_profesor
              SET fecha_devolucion_esperada=?,estado_prestamo=CASE WHEN ?<CURRENT_DATE
              THEN 'Vencido' ELSE 'Activo' END
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              """;
        };
    return Jdbc.update(c, sql, due, due, id);
  }

  public int cancelLoan(Connection c, long id, BorrowerType type, LocalDate date, String reason)
      throws SQLException {
    String sql =
        switch (type) {
          case STUDENT ->
              """
              UPDATE prestamos_estudiante
              SET estado_prestamo='Anulado',fecha_devolucion_real=?,observaciones=?
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              """;
          case TEACHER ->
              """
              UPDATE prestamos_profesor
              SET estado_prestamo='Anulado',fecha_devolucion_real=?,observaciones=?
              WHERE id_prestamo=? AND fecha_devolucion_real IS NULL
              """;
        };
    return Jdbc.update(c, sql, date, reason, id);
  }
}
