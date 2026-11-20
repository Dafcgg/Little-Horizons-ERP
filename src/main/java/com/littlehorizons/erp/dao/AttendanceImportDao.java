package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.AttendanceStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

public interface AttendanceImportDao {
  boolean studentExists(Connection connection, int studentId) throws SQLException;

  boolean subjectExists(Connection connection, int subjectId) throws SQLException;

  long insert(
      Connection connection, int studentId, int subjectId, LocalDate date,
      AttendanceStatus status, AcademicPeriod period) throws SQLException;
}
