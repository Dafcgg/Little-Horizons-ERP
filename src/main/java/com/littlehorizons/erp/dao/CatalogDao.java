package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.*;
import java.sql.*;
import java.util.*;

public interface CatalogDao {
  List<CatalogEntry> list(Connection c, CatalogType type) throws SQLException;

  Optional<CatalogEntry> lock(Connection c, CatalogType type, long id) throws SQLException;

  long create(Connection c, CatalogType type, CatalogEntry entry) throws SQLException;

  int update(Connection c, CatalogType type, CatalogEntry entry) throws SQLException;

  boolean hasReferences(Connection c, CatalogType type, long id) throws SQLException;
}
