-- Ejecutar una sola vez sobre una instalación anterior con año_academico.
-- No aplicar después de importar la versión actual de docs/database.sql.
-- RENAME COLUMN conserva datos, tipos, comentarios, índices y restricciones.
USE erp_little_horizons;

ALTER TABLE matriculas
    RENAME COLUMN año_academico TO anio_academico,
    RENAME INDEX idx_año_grado TO idx_anio_grado;
ALTER TABLE asignaciones_profesor
    RENAME COLUMN año_academico TO anio_academico,
    RENAME INDEX idx_profesor_año TO idx_profesor_anio,
    RENAME INDEX idx_materia_año TO idx_materia_anio;
ALTER TABLE inscripciones_materia
    RENAME COLUMN año_academico TO anio_academico,
    RENAME INDEX idx_estudiante_año TO idx_estudiante_anio;
ALTER TABLE registros_academicos
    RENAME COLUMN año_academico TO anio_academico,
    RENAME INDEX idx_estudiante_año TO idx_estudiante_anio;
ALTER TABLE pagos RENAME COLUMN año_academico TO anio_academico;
ALTER TABLE asistencias RENAME COLUMN año_academico TO anio_academico;
