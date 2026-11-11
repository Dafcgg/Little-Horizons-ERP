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
        "INSERT INTO recursos(codigo_recurso,nombre_recurso,id_categoria,ubicacion_fisica)"
            + " VALUES(?,?,?,?)",
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

  // Table and borrower column are selected exclusively from a closed enum, never from user text.
  private String table(BorrowerType type) {
    return type.databaseValue();
  }

  private String borrower(BorrowerType type) {
    return type == BorrowerType.STUDENT ? "id_estudiante" : "id_profesor";
  }

  public long createLoan(
      Connection c, long resource, long borrower, BorrowerType type, LocalDate date, LocalDate due)
      throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO "
            + table(type)
            + "(id_recurso,"
            + borrower(type)
            + ",fecha_prestamo,fecha_devolucion_esperada) VALUES(?,?,?,?)",
        resource,
        borrower,
        date,
        due);
  }

  private Loan loan(ResultSet r, BorrowerType type) throws SQLException {
    return new Loan(
        r.getLong("id_prestamo"),
        r.getLong("id_recurso"),
        r.getLong(borrower(type)),
        type,
        r.getDate("fecha_devolucion_esperada").toLocalDate());
  }

  public Optional<Loan> findLoan(Connection c, long id, BorrowerType type) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM "
                + table(type)
                + " WHERE id_prestamo=? AND fecha_devolucion_real IS NULL",
            r -> loan(r, type),
            id)
        .stream()
        .findFirst();
  }

  public Optional<Loan> lockLoan(Connection c, long id, BorrowerType type) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM "
                + table(type)
                + " WHERE id_prestamo=? AND fecha_devolucion_real IS NULL FOR UPDATE",
            r -> loan(r, type),
            id)
        .stream()
        .findFirst();
  }

  public void returnLoan(Connection c, long id, BorrowerType type, LocalDate date)
      throws SQLException {
    Jdbc.update(
        c,
        "UPDATE "
            + table(type)
            + " SET estado_prestamo='Devuelto',fecha_devolucion_real=? WHERE id_prestamo=?",
        date,
        id);
  }

  public List<Loan> overdue(Connection c, LocalDate date) throws SQLException {
    List<Loan> rows = new ArrayList<>();
    for (BorrowerType type : BorrowerType.values())
      rows.addAll(
          Jdbc.query(
              c,
              "SELECT * FROM "
                  + table(type)
                  + " WHERE fecha_devolucion_real IS NULL AND fecha_devolucion_esperada<?",
              r -> loan(r, type),
              date));
    return rows;
  }

  public int updateResource(Connection c, SchoolResource r) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE recursos SET codigo_recurso=?,nombre_recurso=?,id_categoria=?,ubicacion_fisica=?"
            + " WHERE id_recurso=?",
        r.code(),
        r.name(),
        r.categoryId(),
        r.location(),
        r.id());
  }

  public boolean activeCategory(Connection c, long category) throws SQLException {
    return !Jdbc.query(
            c,
            "SELECT id_categoria FROM categorias_recurso WHERE id_categoria=? AND esta_activo=TRUE"
                + " FOR UPDATE",
            r -> r.getLong(1),
            category)
        .isEmpty();
  }

  public boolean activeBorrower(Connection c, long id, BorrowerType type) throws SQLException {
    String entity = type == BorrowerType.STUDENT ? "estudiantes" : "profesores";
    return !Jdbc.query(
            c,
            "SELECT "
                + borrower(type)
                + " FROM "
                + entity
                + " WHERE "
                + borrower(type)
                + "=? AND estado='Activo' FOR UPDATE",
            r -> r.getLong(1),
            id)
        .isEmpty();
  }

  private LoanEntry entry(java.sql.ResultSet r, BorrowerType type) throws SQLException {
    java.sql.Date returned = r.getDate("fecha_devolucion_real");
    return new LoanEntry(
        r.getLong("id_prestamo"),
        r.getLong("id_recurso"),
        r.getLong(borrower(type)),
        type,
        r.getDate("fecha_prestamo").toLocalDate(),
        r.getDate("fecha_devolucion_esperada").toLocalDate(),
        returned == null ? null : returned.toLocalDate(),
        r.getString("estado_prestamo"));
  }

  public List<LoanEntry> loans(Connection c, BorrowerType type) throws SQLException {
    return Jdbc.query(
        c, "SELECT * FROM " + table(type) + " ORDER BY id_prestamo", r -> entry(r, type));
  }

  public Optional<LoanEntry> loanEntry(Connection c, long id, BorrowerType type, boolean lock)
      throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM " + table(type) + " WHERE id_prestamo=?" + (lock ? " FOR UPDATE" : ""),
            r -> entry(r, type),
            id)
        .stream()
        .findFirst();
  }

  public int updateDueDate(Connection c, long id, BorrowerType type, LocalDate due)
      throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE "
            + table(type)
            + " SET fecha_devolucion_esperada=?,estado_prestamo=CASE WHEN ?<CURRENT_DATE THEN"
            + " 'Vencido' ELSE 'Activo' END WHERE id_prestamo=? AND fecha_devolucion_real IS NULL",
        due,
        due,
        id);
  }

  public int cancelLoan(Connection c, long id, BorrowerType type, LocalDate date, String reason)
      throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE "
            + table(type)
            + " SET estado_prestamo='Anulado',fecha_devolucion_real=?,observaciones=? WHERE"
            + " id_prestamo=? AND fecha_devolucion_real IS NULL",
        date,
        reason,
        id);
  }
}
