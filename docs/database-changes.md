# Cambios respecto al anexo

Base: [script suministrado en Google Drive](https://drive.google.com/file/d/1fRaR8EUwcNheOWyMK5YJMkW0NWHo5JK7/view?usp=sharing). Se conservan las entidades, campos académicos en español, `DECIMAL(2,1)` y las dos tablas de préstamos.

1. Se elimina el `DROP DATABASE`. La instalación falla si la base ya existe, evitando que un segundo arranque borre información. El SQL no se ejecuta automáticamente desde Java.
2. Los borrados en cascada pasan a `RESTRICT`, para conservar referencias históricas.
3. `grados.nivel_numerico` deja de ser único y recibe un `CHECK` de 0 a 11. La semilla contiene denominaciones base y variantes de sección para cumplir las 30 filas por tabla, sin inventar grados superiores a once. Las secciones de asignación/inscripción siguen siendo explícitas.
4. El parentesco y la responsabilidad financiera se añaden a `estudiante_acudiente`, porque dependen de la relación. Se mantienen los campos originales en acudientes por compatibilidad, pero la aplicación usa los de la relación para autorizar pagos.
5. Sección obligatoria y predeterminada `A` en asignaciones e inscripciones: elimina la ambigüedad de claves únicas con `NULL`.
6. `horarios` representa franjas por asignación. Los servicios serializan cambios por grado y profesor antes de comprobar cruces.
7. `asistencias` incorpora año y período para conservar el contexto académico.
8. `membresias_club.estudiante_activo` es una columna generada: el índice único de club/estudiante activo impide duplicados sin bloquear el historial de retiros. Un bloqueo de club protege el conteo de cupos.
9. `tarifas_estudiante` guarda el valor particular vigente. `obligaciones` registra importe exigible, referencia del cobro y vencimiento, permitiendo calcular deudas sin pagos. La referencia evita cargos duplicados por estudiante/concepto.
10. Se incorpora tasa de IVA configurable a conceptos y copia de ella a obligaciones. Importes no negativos y pagos estrictamente positivos.
11. `pagos.id_obligacion` relaciona abonos y deuda. Los servicios bloquean la obligación antes de comprobar el saldo y registrar un pago.
12. `facturas.id_pago` es único. Se añaden estados de generación, datos históricos de cliente/emisor y totales. `detalles_factura` conserva la descripción y valores facturados. El PDF se genera después del commit y puede reconstruirse sin crear otro pago.
13. Se añaden validaciones de fechas en préstamos. El servicio bloquea el recurso compartido entre las dos tablas de préstamos antes de prestarlo o devolverlo.

El DML incluye al menos 30 filas en las 26 tablas. Los registros de préstamo a estudiantes son históricos y devueltos; los de profesores continúan pendientes, con los recursos marcados como prestados. La prueba `everyTableHasAtLeastThirtyRows` comprueba los mínimos en el servidor real.

## Ampliación CRUD

Se añaden indicadores de actividad a acudientes, grados, categorías y asignaciones; estado de obligación; estado de pago, motivo y FK única al pago original corregido; estado de negocio y motivo en factura; estado Anulado en ambas tablas de préstamos. `docs/migrations/002_complete_crud.sql` actualiza una base previa sin eliminar datos. El script completo ya contiene estas ampliaciones. La anulación de documentos mantiene cabeceras y detalles; los saldos consideran únicamente pagos Confirmados.
