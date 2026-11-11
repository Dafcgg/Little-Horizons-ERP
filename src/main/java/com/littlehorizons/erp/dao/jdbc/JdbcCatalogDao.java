package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.CatalogDao;
import com.littlehorizons.erp.model.*;
import java.sql.*;
import java.util.*;

public final class JdbcCatalogDao implements CatalogDao {
  private CatalogEntry map(ResultSet r, CatalogType type) throws SQLException {
    return new CatalogEntry(
        r.getLong(type.id()),
        r.getString(type.nameColumn()),
        type == CatalogType.GRADE ? r.getInt("nivel_numerico") : 0,
        r.getBoolean("esta_activo"));
  }

  public List<CatalogEntry> list(Connection c, CatalogType t) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM " + t.table() + " ORDER BY " + t.id(), r -> map(r, t));
  }

  public Optional<CatalogEntry> lock(Connection c, CatalogType t, long id) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM " + t.table() + " WHERE " + t.id() + "=? FOR UPDATE",
            r -> map(r, t),
            id)
        .stream()
        .findFirst();
  }

  public long create(Connection c, CatalogType t, CatalogEntry e) throws SQLException {
    return t == CatalogType.GRADE
        ? Jdbc.insert(
            c, "INSERT INTO grados(nombre_grado,nivel_numerico) VALUES(?,?)", e.name(), e.level())
        : Jdbc.insert(c, "INSERT INTO categorias_recurso(nombre_categoria) VALUES(?)", e.name());
  }

  public int update(Connection c, CatalogType t, CatalogEntry e) throws SQLException {
    return t == CatalogType.GRADE
        ? Jdbc.update(
            c,
            "UPDATE grados SET nombre_grado=?,nivel_numerico=?,esta_activo=? WHERE id_grado=?",
            e.name(),
            e.level(),
            e.active(),
            e.id())
        : Jdbc.update(
            c,
            "UPDATE categorias_recurso SET nombre_categoria=?,esta_activo=? WHERE id_categoria=?",
            e.name(),
            e.active(),
            e.id());
  }

  public boolean hasReferences(Connection c, CatalogType t, long id) throws SQLException {
    return t == CatalogType.GRADE
        ? Jdbc.count(
                c,
                "SELECT (SELECT COUNT(*) FROM matriculas WHERE id_grado=?)+(SELECT COUNT(*) FROM"
                    + " materias WHERE id_grado=?)",
                id,
                id)
            > 0
        : Jdbc.count(c, "SELECT COUNT(*) FROM recursos WHERE id_categoria=?", id) > 0;
  }
}
