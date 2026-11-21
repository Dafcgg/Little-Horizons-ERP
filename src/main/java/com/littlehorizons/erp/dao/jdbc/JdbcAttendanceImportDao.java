package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.AttendanceImportDao;
import com.littlehorizons.erp.model.AcademicPeriod;
import com.littlehorizons.erp.model.AttendanceNames;
import com.littlehorizons.erp.model.AttendanceStatus;
import com.littlehorizons.erp.util.BusinessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

public final class JdbcAttendanceImportDao implements AttendanceImportDao {
  @Override
  public AttendanceNames names(Connection connection, Integer studentId, Integer subjectId)
      throws SQLException {
    return Jdbc.query(connection, """
        SELECT (SELECT CONCAT_WS(' ', primer_nombre, NULLIF(segundo_nombre, ''),
                       primer_apellido, NULLIF(segundo_apellido, ''))
                FROM estudiantes WHERE id_estudiante=?) AS estudiante,
               (SELECT nombre_materia FROM materias WHERE id_materia=?) AS materia
        """, row -> new AttendanceNames(row.getString("estudiante"), row.getString("materia")),
        studentId, subjectId).get(0);
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
            "Ya existe una asistencia para ese estudiante, materia y fecha. Revise el historial antes de volver a cargarla.", error);
      }
      throw error;
    }
  }
}
