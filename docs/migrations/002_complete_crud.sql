-- Migración de la base anterior: ejecutar una sola vez sobre erp_little_horizons.
USE erp_little_horizons;
ALTER TABLE acudientes ADD COLUMN esta_activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE grados ADD COLUMN esta_activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE categorias_recurso ADD COLUMN esta_activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE asignaciones_profesor ADD COLUMN esta_activa BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE obligaciones ADD COLUMN estado ENUM('Activa','Anulada') NOT NULL DEFAULT 'Activa';
ALTER TABLE pagos ADD COLUMN estado_pago ENUM('Confirmado','Anulado') NOT NULL DEFAULT 'Confirmado', ADD COLUMN motivo_anulacion VARCHAR(255), ADD COLUMN id_pago_original INT UNIQUE, ADD CONSTRAINT fk_pago_original FOREIGN KEY(id_pago_original) REFERENCES pagos(id_pago);
ALTER TABLE facturas ADD COLUMN estado_factura ENUM('Emitida','Anulada') NOT NULL DEFAULT 'Emitida', ADD COLUMN motivo_anulacion VARCHAR(255);
ALTER TABLE prestamos_estudiante MODIFY estado_prestamo ENUM('Activo','Devuelto','Vencido','Anulado') DEFAULT 'Activo';
ALTER TABLE prestamos_profesor MODIFY estado_prestamo ENUM('Activo','Devuelto','Vencido','Anulado') DEFAULT 'Activo';
