package com.littlehorizons.erp.util;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionProvider {
  Connection open() throws SQLException;
}
