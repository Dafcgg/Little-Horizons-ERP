package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.AttendanceImportDao;
import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.AttendanceStatus;
import com.littlehorizons.erp.util.BusinessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

public final class JdbcAttendanceImportDao implements AttendanceImportDao {
  @Override
  public boolean studentExists(Connection connection, int studentId) throws SQLException {
    return Jdbc.count(connection, "SELECT COUNT(*) FROM estudiantes WHERE id_estudiante=?", studentId)
        > 0;
  }

  @Override
  public boolean subjectExists(Connection connection, int subjectId) throws SQLException {
    return Jdbc.count(connection, "SELECT COUNT(*) FROM materias WHERE id_materia=?", subjectId) > 0;
  }

  @Override
  public long insert(
      Connection connection, int studentId, int subjectId, LocalDate date,
      AttendanceStatus status, AcademicPeriod period) throws SQLException {
    try {
      return Jdbc.insert(
          connection,
          """
          INSERT INTO asistencias (
            id_estudiante, id_materia, fecha_asistencia, estado_asistencia,
            anio_academico, periodo_academico, registrado_por, observaciones
          ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL)
          """,
          studentId, subjectId, date, status.databaseValue(), date.getYear(), period.databaseValue());
    } catch (SQLException error) {
      if (error.getErrorCode() == 1062) {
        // The transaction manager rolls back the row and logs the original SQL cause.
        throw new BusinessException(
            "Ya existe una asistencia para ese estudiante, materia y fecha.", error);
      }
      throw error;
    }
  }
}
