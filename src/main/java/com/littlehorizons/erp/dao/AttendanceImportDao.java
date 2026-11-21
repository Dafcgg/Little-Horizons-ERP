package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.AttendanceNames;
import com.littlehorizons.erp.model.AttendanceStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

public interface AttendanceImportDao {
  AttendanceNames names(Connection connection, Integer studentId, Integer subjectId)
      throws SQLException;

  long insert(
      Connection connection, int studentId, int subjectId, LocalDate date,
      AttendanceStatus status, AcademicPeriod period) throws SQLException;
}
