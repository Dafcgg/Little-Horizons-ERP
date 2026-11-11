# Bitácora de implementación y auditoría — Little Horizons ERP

## 1. Estado de la entrega

Revisión local del 10 de noviembre de 2026 sobre fuentes, Maven, DDL/DML, migración, consola y pruebas. Resultado actual: **62 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**; `mvn clean verify` terminó con `BUILD SUCCESS`.

Se completaron las operaciones de los cuatro módulos: académico, extracurricular, financiero y recursos. La supresión conserva el historial mediante retiro, inactivación, baja o anulación cuando corresponde. Las claves históricas y los documentos financieros emitidos se corrigen mediante operaciones trazables, no sobrescribiendo su identidad.

La evolución de pruebas fue 36 → 41 → 61 → 62. La última revisión encontró una regresión de A02 en la edición del estudiante, la corrigió y añadió una prueba específica. Los identificadores A01–A12 se mantienen en la matriz de esta bitácora.

## 2. Arquitectura y responsabilidades verificadas

```text
src/main/java/com/littlehorizons/erp/
├── model/
├── view/
├── controller/
├── service/
├── dao/
│   └── jdbc/
├── util/
└── Main.java
```

No se incorporaron paquetes `config/` o `exception/`. Conexiones, PDF, logging y excepciones compartidas permanecen en `util/`.

| Capa | Responsabilidad y comprobación |
| --- | --- |
| `model/` | Records y enums del dominio; sin lectura de consola ni apertura de conexiones. |
| `view/` | Entrada, validación sintáctica y presentación en español; sin SQL, imports JDBC ni acceso a DAO. |
| `controller/` | Recopila datos, invoca servicios y presenta resultados; sin consultas ni límites transaccionales. |
| `service/` | Valida negocio, precisión y estados; delimita unidades de trabajo y decide cálculos financieros. |
| `dao/jdbc/` | Ejecuta consultas parametrizadas, bloqueos, mapeos y escrituras usando la conexión recibida; no solicita datos por consola. |
| `util/` | Implementa la fábrica de conexiones y las herramientas compartidas, sin apropiarse de la transacción del negocio. |

Las búsquedas estáticas con `rg` no encontraron SQL/JDBC en vistas o controladores, ni `Scanner`, `System.in`, `System.out` o `ConsoleView` en DAO. Las llamadas de producción a `setTransactionIsolation`, `setAutoCommit`, `commit` y `rollback` se encuentran únicamente en `service/TransactionalService.java`.

## 3. Propiedad de la conexión y transacciones

`DatabaseConnection`, implementación de `ConnectionProvider`, abre una conexión nueva mediante `DriverManager`. No la conserva en un campo ni la confirma. Su propietario es el servicio que la obtiene.

`TransactionalService.transaction` utiliza `try-with-resources`, fija `READ_COMMITTED`, desactiva autocommit y comparte la misma conexión entre los DAO participantes. Confirma después de completar la unidad de trabajo. Ante `SQLException`, `RuntimeException` o `Error`, intenta rollback y preserva como suprimido cualquier fallo adicional de rollback. Finalmente cierra la conexión, también ante errores de configuración inicial.

`Jdbc` cierra `PreparedStatement`, `ResultSet` y claves generadas mediante `try-with-resources`. Los DAO no cierran la conexión recibida, porque podría seguir utilizándola otro DAO de la misma operación. Los nombres variables de tablas/columnas se seleccionan mediante enums cerrados `BorrowerType` y `CatalogType`; los valores de usuario se enlazan como parámetros.

Las ocho pruebas de `ResourceLifecycleTest` verifican confirmación, reversión y cierre ante éxito, fallo SQL, excepción de negocio, error, fallo de commit, fallo de rollback, fallo inicial y fallo de mapeo. No se detectaron fugas JDBC en los recorridos inspeccionados y probados.

## 4. Integración Maven

Coordenadas: `com.littlehorizons:little-horizons-erp:1.0.0-SNAPSHOT`. El POM configura UTF-8 y `maven.compiler.release=17`.

| Dependencia | Versión | Función |
| --- | --- | --- |
| `com.mysql:mysql-connector-j` | 26.10.0 | Driver JDBC y descubrimiento automático del proveedor. |
| `org.apache.pdfbox:pdfbox` | 3.0.8 | Documento, páginas, fuente y escritura del PDF. |
| `com.google.zxing:core` | 3.5.4 | Codificación QR; decodificación de verificación en pruebas. |
| `com.google.zxing:javase` | 3.5.4 | Conversión de QR a imagen para incrustarla. |
| `org.junit.jupiter:junit-jupiter` | 5.11.4 | Pruebas; alcance Maven `test`. |
| `java.util.logging` | Incluido en Java 17 | Logging a archivo. |

Plugins: Compiler 3.13.0, Surefire 3.5.2, JAR 3.4.2, Exec 3.5.0 y Shade 3.6.0. JAR usa `forceCreation=true` para impedir reutilizar como entrada un archivo previamente combinado por Shade. El script de integración realiza compilación limpia.

Shade establece `Main`, incorpora dependencias, combina proveedores de servicios JDBC y configura transformadores de avisos/licencias. Se excluyen firmas de los JAR originales del ensamblado. El resultado es `target/little-horizons-erp-1.0.0-SNAPSHOT.jar`.

## 5. Cierre CRUD por módulo

| Módulo | Operaciones implementadas y restricciones |
| --- | --- |
| Académico: personas y catálogos | Alta, consulta y edición de estudiantes, acudientes, profesores, grados y materias; retiro/inactivación y reactivación. El último acudiente activo no puede eliminarse ni inactivarse. Los vínculos permiten editar parentesco, contacto principal, emergencia y responsabilidad financiera. |
| Académico: relaciones y registros | Matrículas, asignaciones, horarios e inscripciones consultables y editables, con retiro/inactivación o eliminación de franjas. Las relaciones con historial conservan su identidad. Notas y asistencias admiten registro, consulta, corrección y eliminación. El cierre de materia exige I–IV y se invalida al corregir/eliminar una nota. |
| Extracurricular | Alta, consulta, edición e inactivación/reactivación de clubes y eventos; membresías con ingreso, fecha, retiro y restauración; participantes con alta, consulta, corrección de posición y eliminación. Inactivar un club retira sus membresías activas en la misma transacción. |
| Financiero | Conceptos y tarifas particulares; obligaciones consultables, editables y anulables; pagos y facturas con consulta, anulación y corrección por reemplazo. Se conservan originales, recibos, detalles y relación `id_pago_original`. El reemplazo y la anulación original son atómicos. |
| Recursos | Categorías e inventario con alta, consulta, edición e inactivación/baja; préstamos separados por solicitante, consulta de historial, modificación de vencimiento, devolución y anulación. Un préstamo cerrado no se edita y un recurso prestado no puede darse de baja. |

La opción **6. Administración, edición e inactivación** del menú principal reúne las pantallas generales. Los formularios muestran valores actuales; Enter los conserva y `q` cancela. Las altas ordinarias siguen disponibles en los menús funcionales. La [matriz CRUD](crud_matrix.md) detalla las operaciones de las 26 tablas.

## 6. Exclusividad y concurrencia

- **Clubes:** se bloquea la fila del club con `FOR UPDATE` antes de contar miembros y comprobar capacidad. El mismo protocolo se usa al ingresar, restaurar membresías y editar cupos. El índice único de membresía activa impide duplicar una pareja estudiante/club.
- **Recursos:** estudiantes y profesores bloquean la misma fila de `recursos` antes de comprobar disponibilidad. Inserción del préstamo y cambio a Prestado se confirman juntos. Devolución y anulación vuelven a bloquear el recurso y liberan disponibilidad conservando el historial.
- **Finanzas:** la fila de obligación se bloquea antes de consultar abonos y admitir pagos, anulaciones o reemplazos. Solo los pagos Confirmados se incluyen en el saldo.
- **Académico:** horarios y reactivaciones revalidan cruces; notas y cierres de materia se serializan mediante la fila del estudiante.

Las pruebas de estrés ejecutan 100 solicitudes de inscripción: 15 confirmadas para cinco clubes de tres plazas y 85 rechazos esperados. Otros 100 intentos de préstamo producen exactamente un préstamo por recurso en cinco rondas: 5 confirmados y 95 rechazos esperados. También se prueban restauración de membresía contra alta y reducción de cupo contra alta.

La capacidad agregada y la exclusividad entre las dos tablas de préstamos dependen del servicio y sus bloqueos. No existe un `CHECK` que cuente membresías ni una restricción declarativa cruzada entre ambas tablas. Escrituras directas que eludan el servicio pueden saltarse esas reglas.

## 7. IVA, totales y precisión

**A01 permanece corregido:** `FinanceService` calcula los importes de obligaciones, subtotal e IVA de cada pago con `BigDecimal`. `JdbcFinanceDao.quote` devuelve datos mediante `FeeQuote`; `createObligation`, `insertInvoice` e `insertLine` reciben los importes ya calculados.

```text
obligación = redondear(tarifa × (1 + tasaIVA), 2, HALF_UP)
subtotal del abono = redondear(abono / (1 + tasaIVA), 2, HALF_UP)
IVA del abono = abono − subtotal del abono
total de factura = importe del abono confirmado
saldo disponible = obligación − abonos confirmados
```

Los importes no pueden ser negativos, exceder `DECIMAL(10,2)` ni tener precisión significativa superior a dos decimales. La tasa está entre 0 y 1, con máximo cuatro decimales. El total con IVA vuelve a validarse antes de persistir. Una beca total permite obligación cero, que no admite pagos positivos.

El DAO sí utiliza `SUM` y consultas de saldo para agregación y reportes; esas consultas no calculan el IVA ni determinan los importes emitidos. La autorización `0 < pago <= saldo`, la reducción de obligación y las decisiones de anulación pertenecen al servicio.

`AcademicService` rechaza `5.5` y `3.45` antes de guardar notas. MySQL agrega rango mediante `CHECK` y almacenamiento `DECIMAL(2,1)`. Se verificó que una escritura SQL directa de `3.45` puede redondearse a `3.5`: la precisión estricta de entrada es responsabilidad de Java.

## 8. PDF después del commit y recuperación

1. El servicio confirma pago, cabecera y detalle de factura dentro de una transacción SQL. Un fallo SQL revierte el conjunto.
2. Después del retorno de esa transacción, con su conexión cerrada, recupera la factura histórica y llama a `InvoiceRenderer`.
3. PDFBox escribe un temporal en el directorio final. Documento y streams se cierran con `try-with-resources`. Se ajusta el texto al ancho de la fuente, se pagina a 34 líneas y se reserva espacio para el QR de ZXing.
4. El temporal se mueve al destino con operación atómica si está soportada; en caso contrario se usa reemplazo normal. El `finally` intenta eliminar el temporal sobrante.
5. Una transacción independiente verifica el estado de negocio y guarda Generada, ruta y nombre de archivo.
6. Si falla la emisión inicial o la escritura de una factura anulada, el pago/anulación permanece confirmado y se intenta guardar Fallida. La regeneración por ID reconstruye el mismo documento. Si también falla el acceso a MySQL, el estado puede permanecer sin actualizar.

Repetir un pago vigente con el mismo recibo y datos recupera los mismos IDs, número y CUFE. Cambiar los datos de ese recibo o reutilizar un recibo anulado se rechaza. Corregir un pago utiliza recibo nuevo y conserva el enlace al original.

La generación se serializa por factura dentro de la JVM. Antes de marcar Generada se verifica que la factura no haya cambiado, por ejemplo a Anulada. Una regeneración fallida no degrada automáticamente un estado previamente Generada; esta protección no sustituye comprobar físicamente un archivo ante daños externos.

No hay transacción distribuida MySQL/archivos. Una interrupción puede dejar un PDF pendiente o un archivo sin estado actualizado. Un commit de resultado incierto se resuelve consultando o reintentando con el mismo recibo. Varias JVM con directorio compartido requieren coordinación adicional de archivos.

## 9. Verificación de A01–A12

| ID | Estado actual y evidencia |
| --- | --- |
| A01 | Conforme. Cálculo de IVA e importes en `FinanceService`; DAO recibe valores. Pruebas de IVA, tarifas, sobrepago y desbordamiento. |
| A02 | Regresión localizada y corregida en esta revisión: `updateStudent` ahora valida estado no nulo antes de `Set.of(...).contains`. Nueva prueba exige `BusinessException` y ninguna conexión abierta. Se conservan las demás validaciones nulas. |
| A03 | Conforme. La autorización de notas/asistencia exige matrícula, estudiante, inscripción, profesor y asignación activos, además de materia disponible. Prueba de matrícula retirada. |
| A04 | Conforme. Matrícula nueva exige estudiante activo, acudiente y grado disponible. Prueba de rechazo de estudiante retirado. |
| A05 | Conforme. Una fecha de emisión se captura en el servicio y se comparte entre cabecera y CUFE. Prueba de hash contra factura persistida. |
| A06 | Conforme. El renderizador valida caracteres con la fuente real y sustituye los no codificables. Prueba de texto latino, controles y emoji. |
| A07 | Conforme. Ajuste por ancho y paginación; prueba extensa conserva último concepto, totales y CUFE. |
| A08 | Conforme. Ruta y `nombre_archivo_pdf` se actualizan al registrar generación; comprobación en MySQL. |
| A09 | Conforme. `PaymentMethod.CHEQUE` corresponde al enum SQL; pago y reintento idempotente probados. |
| A10 | Conforme. Asociación inicial principal, de emergencia y financiera; comprobación explícita en integración. La edición protege al último acudiente activo y mantiene principal activo. |
| A11 | Conforme por inspección. Si falla la configuración de `FileHandler`, el bloque de error lo cierra. No se añadió una prueba que inyecte ese fallo interno del logger. |
| A12 | Conforme. Las precondiciones de horarios, inscripción y posiciones se deciden en servicios; DAO consulta, bloquea, mapea y escribe. Sin lectura de consola ni límites transaccionales en DAO. |

El estado final inspeccionado conserva las correcciones históricas; no se afirma que la regresión A02 nunca haya existido durante la ampliación CRUD.

## 10. Pruebas, esquema y alcance

| Suite | Pruebas aprobadas |
| --- | --- |
| `CoreRulesTest` | 6 |
| `ConsoleViewTest` | 2 |
| `ResourceLifecycleTest` | 8 |
| `PdfInvoiceGeneratorTest` | 3 |
| `MavenStructureTest` | 1 |
| `DatabaseIntegrationTest` | 18 |
| `StressScenariosTest` | 4 |
| `CompleteCrudIntegrationTest` | 20 |
| **Total** | **62** |

Son 20 pruebas independientes de MySQL y 42 de integración. Se ejecutan mediante:

```bash
./scripts/test-integration.sh
```

El script inicia MySQL 8 aislado, carga `docs/database.sql`, ejecuta `mvn clean verify` y elimina contenedor/volúmenes al terminar. Los informes quedan en `target/surefire-reports/`. Los fallos deliberados generan entradas esperadas en `error_log.txt`.

El esquema contiene 26 tablas con al menos 30 filas cada una. `docs/migrations/002_complete_crud.sql` actualiza una instalación de la fase anterior una sola vez, sin borrar datos; su ejecución y conservación de registros se verificaron durante el cierre CRUD. La prueba de estructura exige raíz Maven, paquetes coherentes y bytecode Java 17, versión 61.

El alcance incluye los recorridos CRUD y fallos probados. No certifica ausencia universal de defectos, rendimiento institucional, recuperación de backups, auditoría de seguridad de todas las dependencias ni integración DIAN real. Autenticación, vigencias de tarifas, cobros recurrentes automáticos y entrega GitHub continúan fuera de esta implementación. Autores, licencia y contactos deben completarse con datos reales.
