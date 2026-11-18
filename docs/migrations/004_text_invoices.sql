-- Apply to an existing database with migrations 002 and 003 already installed.
USE erp_little_horizons;

-- Rename the legacy file columns for text invoice generation.
-- Keep recorded dates, statuses, historical paths and CUFE values.
-- A missing issuance time remains NULL for invoices issued before this migration.
ALTER TABLE facturas
    ADD COLUMN hora_emision TIME NULL COMMENT 'Hora de emisión en Bogotá' AFTER fecha_emision,
    CHANGE COLUMN nombre_archivo_pdf nombre_archivo VARCHAR(255) COMMENT 'Nombre del archivo generado',
    MODIFY COLUMN cliente_nombre VARCHAR(403) NOT NULL,
    MODIFY COLUMN ruta_archivo VARCHAR(500) COMMENT 'Ruta completa del archivo de factura',
    CHANGE COLUMN estado_pdf estado_archivo ENUM('Pendiente','Generada','Fallida')
        NOT NULL DEFAULT 'Pendiente';
