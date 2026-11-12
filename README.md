# Little Horizons ERP

## 1. Título del proyecto

Sistema de gestión educativa del Colegio Internacional Little Horizons: aplicación Java 17 de consola con arquitectura MVC extendida mediante DAO y servicios transaccionales.

## 2. Descripción

Centraliza gestión académica, actividades extracurriculares, finanzas y préstamos de recursos. Permite consultar rendimiento, asistencia, participación, obligaciones pendientes e inventario, sustituyendo registros dispersos por información relacionada en MySQL.

Incluye CRUD de las 26 entidades del esquema, pantallas de edición/inactivación y facturas PDF con CUFE, QR y resolución simulados. Las bajas conservan el historial cuando corresponde. Corregir un documento financiero anula el original y crea un reemplazo trazable.

## 3. Tecnologías utilizadas


| Tecnología / dependencia Maven    | Versión              | Uso                                               |
| --------------------------------- | -------------------- | ------------------------------------------------- |
| Java                              | 17                   | Records, enums, BigDecimal, lambdas y Stream API. |
| Maven                             | Verificado con 3.6.3 | Dependencias, compilación, pruebas y empaquetado. |
| MySQL                             | 8.0.16 o superior    | Persistencia InnoDB, FK, índices y CHECK.         |
| `com.mysql:mysql-connector-j`     | 26.10.0              | Driver JDBC.                                      |
| `org.apache.pdfbox:pdfbox`        | 3.0.8                | Escritura de facturas PDF.                        |
| `com.google.zxing:core`           | 3.5.4                | Generación y verificación QR.                     |
| `com.google.zxing:javase`         | 3.5.4                | Conversión del QR a imagen.                       |
| `org.junit.jupiter:junit-jupiter` | 5.11.4               | Pruebas, con alcance `test`.                      |
| `java.util.logging`               | Incluido en Java 17  | Logs de aplicación.                               |


`pom.xml` fija UTF-8 y `maven.compiler.release=17`. Plugins: Compiler 3.13.0, Surefire 3.5.2, JAR 3.4.2, Exec 3.5.0 y Shade 3.6.0. JAR fuerza su reconstrucción; Shade genera el ejecutable con dependencias y combina el registro de proveedores JDBC.

IDE: pendiente de indicación del trainer. No se utiliza ORM y la compilación no depende del editor.

## 4. Requerimientos previos

- JDK 17 y Maven; comprobar con `java -version` y `mvn -version`.
- MySQL 8.0+, mínimo **8.0.16** para aplicar los `CHECK` utilizados.
- Credenciales de instalación y cuenta de aplicación con permisos de lectura/escritura.
- Acceso a Maven Central para descargar dependencias la primera vez.
- Permisos de escritura para logs y directorio de facturas.
- Bash y Docker para las pruebas de integración aisladas.

Entorno auditado: Java 17.0.7, Maven 3.6.3 y MySQL de la imagen `mysql:8.0`. Ejecutar con zona horaria `America/Bogota` para mantener coherencia con las fechas escolares del proyecto.

## 5. Configuración de la base de datos

### 5.1. Instalación nueva

El repositorio incluye [docs/database.sql](docs/database.sql), con las secciones DDL y DML necesarias para instalar y probar la base de datos desde un clon. La migración para instalaciones anteriores está en [docs/migrations/002_complete_crud.sql](docs/migrations/002_complete_crud.sql).

Desde la raíz, sobre un servidor sin la base del proyecto:

```bash
mysql -u root -p --default-character-set=utf8mb4 < docs/database.sql
```

El script crea `erp_little_horizons`, separa DDL/DML y carga **26 tablas con al menos 30 registros cada una**. Incluye claves, índices, restricciones únicas y `CHECK`; utiliza UTF-8 e InnoDB. Hay 60 obligaciones iniciales: 30 pagadas y 30 pendientes. Los datos son sintéticos y sus fechas de demostración corresponden a 2026.

No contiene `DROP DATABASE` ni se ejecuta automáticamente al abrir Java. Es instalación inicial, no migración idempotente: se detiene si la base ya existe. El DDL no se revierte como una sola transacción; revisar una instalación incompleta antes de repetirla. El DML sí se agrupa en una transacción.

La semilla de grados contiene las 12 denominaciones base y 18 variantes por sección para satisfacer el mínimo de filas, con niveles 0–11. Se conservan `prestamos_estudiante` y `prestamos_profesor` como tablas separadas.

### 5.2. Actualizar una instalación anterior

Aplicar **una sola vez**, únicamente a la base de la fase previa al CRUD completo:

```bash
mysql -u root -p --default-character-set=utf8mb4 < docs/migrations/002_complete_crud.sql
```

Añade actividad, estados de anulación, motivos y vínculo al pago corregido sin eliminar datos. No aplicar sobre una instalación nueva del script completo ni repetirla. La migración se verificó durante el cierre CRUD.

### 5.3. Cuenta y credenciales

Crear una cuenta con contraseña propia:

```sql
CREATE USER 'little_horizons'@'localhost' IDENTIFIED BY 'REEMPLAZAR_POR_CLAVE_PROPIA';
GRANT SELECT, INSERT, UPDATE, DELETE
ON erp_little_horizons.* TO 'little_horizons'@'localhost';
```

Ejemplo de configuración Bash:

```bash
export ERP_DB_URL='jdbc:mysql://localhost:3306/erp_little_horizons?serverTimezone=America/Bogota'
export ERP_DB_USER='little_horizons'
read -rs -p 'Contraseña MySQL: ' ERP_DB_PASSWORD
export ERP_DB_PASSWORD
export ERP_INVOICE_DIR='invoices'
```

`.env.example` es una referencia y no se carga automáticamente. No versionar credenciales reales.


| Variable             | Comportamiento                                                                  |
| -------------------- | ------------------------------------------------------------------------------- |
| `ERP_DB_URL`         | Predeterminado: MySQL local, base `erp_little_horizons`, zona `America/Bogota`. |
| `ERP_DB_USER`        | Predeterminado: `little_horizons`.                                              |
| `ERP_DB_PASSWORD`    | Obligatoria para conectar; sin valor predeterminado.                            |
| `ERP_INVOICE_DIR`    | Predeterminado: `invoices`, relativo al directorio de ejecución.                |
| `ERP_SCHOOL_NAME`    | Nombre de emisor; valor de demostración identificado como DEMO.                 |
| `ERP_SCHOOL_NIT`     | Identificador de demostración marcado SIMULADO.                                 |
| `ERP_SCHOOL_ADDRESS` | Dirección de emisor configurable.                                               |
| `ERP_SCHOOL_PHONE`   | Teléfono de emisor configurable.                                                |
| `ERP_SCHOOL_EMAIL`   | Correo de emisor; predeterminado en `example.invalid`.                          |


### 5.4. Driver JDBC

Maven descarga Connector/J. JDBC descubre su proveedor automáticamente y Shade conserva ese registro en el JAR. No es necesario copiar el driver al IDE ni llamar a `Class.forName`.

## 6. Instalación y ejecución

### 6.1. Obtener y abrir

Clonar el repositorio, abrir `pom.xml` y configurar JDK 17 en el IDE:

```bash
git clone https://github.com/Dafcgg/Little-Horizons-ERP.git
cd Little-Horizons-ERP
```

### 6.2. Compilar y ejecutar

Después de instalar o migrar la base y exportar variables:

```bash
mvn clean verify
java -Duser.timezone=America/Bogota -jar target/little-horizons-erp-1.0.0-SNAPSHOT.jar
```

Alternativa de desarrollo: `mvn exec:java`.

```text
1. Estudiantes, acudientes y matrícula
2. Académico, profesores y asistencia
3. Clubes y eventos
4. Finanzas y facturación
5. Inventario y préstamos
6. Administración, edición e inactivación
0. Volver / salir
```

La opción 6 reúne seis pantallas administrativas, incluidos grados y categorías. `0` vuelve al menú anterior y `q` cancela la operación. Enter conserva el valor actual en formularios de edición. Fechas: `AAAA-MM-DD`; horas: `HH:MM`; importes con punto o coma decimal. La entrada cerrada termina la consola.

El menú abre sin conectarse; cada operación obtiene su conexión cuando la necesita. La falta de contraseña genera un mensaje de configuración.

### 6.3. Verificación completa

```bash
./scripts/test-integration.sh
```

El script crea un MySQL desechable con puerto local dinámico, espera una consulta TCP válida, instala el SQL y ejecuta `mvn clean verify`. Elimina contenedor y volúmenes al terminar. Sus credenciales solo corresponden a ese entorno de pruebas.

Resultado actual: **62 aprobadas, 0 fallos, 0 errores y 0 omitidas**: 20 independientes de MySQL y 42 de integración. Informes en `target/surefire-reports/`.

Las pruebas MySQL solo se habilitan con `ERP_TEST_DB_URL`. El script configura también `ERP_TEST_DB_USER` y `ERP_TEST_DB_PASSWORD`. Sin esa URL, Maven ejecuta las 20 pruebas independientes y omite las 42 de integración. Las variables de prueba deben apuntar a una base aislada, porque los escenarios insertan y modifican registros.

## 7. Arquitectura MVC + DAO/Service

La entrega utiliza la estructura estándar de Maven. Incluye el código, las pruebas, el SQL y los archivos necesarios para compilar y ejecutar:

```text
Little-Horizons-ERP/
├── pom.xml
├── .gitignore
├── .env.example
├── README.md
├── docs/
│   ├── database.sql
│   └── migrations/
│       └── 002_complete_crud.sql
├── scripts/
│   └── test-integration.sh
└── src/
    ├── main/java/com/littlehorizons/erp/
    └── test/java/com/littlehorizons/erp/
```

Los paquetes de producción se organizan así:

```text
src/main/java/com/littlehorizons/erp/
├── model/       Records y enums
├── view/        ConsoleView y presentación en español
├── controller/  Flujos funcionales y administrativos
├── service/     Negocio y unidades de trabajo
├── dao/         Contratos de persistencia
│   └── jdbc/    Consultas, bloqueos y mapeos
├── util/        Conexión, PDF, logging y excepciones
└── Main.java    Composición de dependencias y menú
```

El flujo es vista → controlador → servicio → DAO → MySQL. No hay SQL en la vista ni lectura de consola en DAO. No existen paquetes raíz `config/` o `exception/`.

La conexión pertenece al servicio. `TransactionalService` fija `READ_COMMITTED`, abre/cierra la conexión y ejecuta commit/rollback. Los DAO reciben esa conexión y no la confirman ni la cierran. `Jdbc` encapsula `PreparedStatement` y cierre de sentencias/resultados.

`docs/database.sql` forma parte de la entrega exigida. Los planes de trabajo, reportes temporales de auditoría y evidencias locales quedan excluidos mediante `.gitignore`.

## 8. Principios SOLID y patrones aplicados

- **S — Responsabilidad única:** `ConsoleView` lee/presenta; `FinanceService` valida y calcula; `JdbcFinanceDao` persiste; `PdfInvoiceGenerator` escribe archivos.
- **O — Abierto/cerrado:** `InvoiceRenderer` permite sustituir el generador sin modificar la transacción financiera.
- **L — Sustitución:** las implementaciones DAO respetan la conexión del llamador y sus contratos; las pruebas verifican su comportamiento integrado.
- **I — Segregación:** contratos separados para estudiantes, académico, actividades, finanzas, recursos y catálogos.
- **D — Inversión de dependencias:** servicios reciben interfaces DAO, `ConnectionProvider` e `InvoiceRenderer` por constructor. Las pruebas sustituyen el renderizador para provocar fallos de disco.

Patrones: MVC extendido, DAO, inyección por constructor, estrategia de renderizado y unidad de trabajo transaccional. `AcademicService` utiliza lambdas y Stream API en promedios. Las tablas variables se eligen desde enums cerrados, nunca desde texto de usuario.

## 9. Módulos y funcionalidades

### 9.1. Académico

CRUD de estudiantes, acudientes, profesores, grados y materias, con retiro/inactivación y reactivación. Vínculos de acudientes editables y protección del último acudiente activo. La asociación inicial queda principal, de emergencia y financiera.

Matrículas, asignaciones e inscripciones permiten consulta, edición y retiro/inactivación; las claves con historial quedan protegidas. Los horarios permiten crear, consultar, editar y eliminar franjas, detectando cruces de profesor/grupo, también al reactivar asignaciones antiguas.

Notas y asistencias admiten registro, consulta, corrección y eliminación. Para registrar se exige matrícula, estudiante, inscripción, profesor y asignación activos y materia disponible. Las notas usan `DECIMAL(2,1)`, rango 0.0–5.0 y validación de precisión en Java: `5.5` y `3.45` se rechazan. SQL directo puede redondear precisión excesiva; el servicio es la barrera de entrada estricta.

Promedios con pesos iguales y `HALF_UP`, sin convertir notas ausentes en cero ni contar Final como quinto período. El cierre de materia exige cuatro períodos y se invalida al corregir/eliminar una nota.

Asistencia: presente, ausente, tardanza y excusa médica. El reporte cuenta registros Ausente por materia en un rango; para alerta semanal se consulta lunes–domingo con umbral 3. No cuenta días distintos, tardanzas ni excusas como ausencias.

### 9.2. Extracurricular

Clubes y eventos con alta, consulta, edición e inactivación/reactivación. Membresías con ingreso, consulta, edición de fecha/estado, retiro y restauración. Cupos protegidos por bloqueo del club; reducir capacidad no puede dejar sobrecupo. Inactivar un club retira sus miembros activos conservando historial.

Participantes con alta, consulta, edición de posiciones y eliminación. Las posiciones solo se admiten en competencias; deben retirarse antes de convertir el evento a no competitivo.

### 9.3. Financiero

Conceptos y tarifas particulares con consulta, edición e inactivación/eliminación de tarifa. Obligaciones con referencia única, vencimiento, consulta, edición y anulación. Una tarifa particular es el precio final antes de IVA; permite beca total con cero y no admite negativos.

`FinanceService` calcula en Java importes, subtotal e IVA con `BigDecimal`, máximo dos decimales; valida tasa entre 0 y 1 y desbordamientos. El DAO recibe esos importes y mantiene consultas agregadas de pagos/saldos para reportes. No calcula el IVA ni el total emitido.

Pagos parciales, recibos únicos, historial individual/familiar y mora de más de un mes calendario. Métodos: efectivo, transferencia, tarjeta débito/crédito y cheque. El pago debe cumplir `0 < pago <= saldo`; el bloqueo de obligación evita sobrepagos concurrentes. No se reduce una obligación por debajo de abonos confirmados ni se anula mientras conserva pagos confirmados.

Pagos y facturas se consultan y anulan preservando originales. La corrección anula el anterior y crea reemplazo en una sola transacción, con recibo nuevo y vínculo al original. Un fallo revierte también la anulación. Los pagos anulados no cuentan para el saldo, pero permanecen en el historial, incluso si se elimina después el vínculo del acudiente con el estudiante.

Pago, cabecera y detalle se confirman antes de escribir PDF. Si falla la emisión inicial, el pago persiste y se intenta marcar Fallida para regeneración. El mismo recibo y datos recuperan el pago vigente sin duplicarlo; un recibo anulado no se reutiliza.

El PDF conserva emisor/acudiente e importes históricos e incluye CUFE SHA-256, QR y resolución simulados. Se guarda como `invoices/factura_000123.pdf`; pagina documentos extensos y sustituye caracteres no admitidos por la fuente. El PDF de una factura anulada muestra ese estado y su motivo.

### 9.4. Recursos

CRUD de categorías e inventario, con baja/inactivación y reactivación cuando corresponda. Los recursos tienen código único, categoría, ubicación y estado; no se dan de baja mientras están prestados.

Préstamos en tablas separadas para estudiantes/profesores, consulta de historial, edición de vencimiento, devolución y anulación. Las operaciones bloquean la misma fila de recurso, evitando préstamos simultáneos entre ambas tablas. Devolver o anular libera disponibilidad y conserva el registro. Los préstamos cerrados no se editan; vencidos se detectan por fecha esperada y ausencia de devolución.

## 10. Manejo de errores y logs

`BusinessException` comunica validaciones recuperables. Las excepciones SQL y errores de renderizado se registran con su causa; la consola muestra mensajes en español y permite continuar. Los fallos deliberados de pruebas producen entradas esperadas.

`AppLogger` usa `java.util.logging` y `FileHandler` UTF-8 en `error_log.txt`, relativo al directorio de ejecución, con límite aproximado de 1 MB y un archivo de rotación. No conserva un histórico ilimitado; instancias concurrentes pueden usar sufijos de bloqueo. Si falla su configuración, se cierra el manejador; si no puede abrirse el log, `Main` informa y termina.

Conexiones, sentencias, resultados y documentos se cierran mediante `try-with-resources`. Un fallo de rollback queda suprimido sobre el error original.

No existe transacción distribuida entre MySQL y archivos. El PDF utiliza temporal y movimiento atómico cuando está soportado. Los estados se actualizan en otra transacción; si falla MySQL después del archivo, puede quedar un estado pendiente. Se recupera por ID de factura o recibo, sin registrar otro pago.

La generación se serializa dentro de la JVM y verifica el estado de negocio antes de marcar Generada. Una regeneración fallida puede conservar un estado previamente Generada; no constituye una comprobación de integridad física del archivo. Varias JVM compartiendo directorio necesitan coordinación adicional.

`.gitignore` excluye credenciales locales, configuración del editor, logs, compilados, facturas generadas, reportes de auditoría y el script histórico de simulación de commits. Conserva el SQL de instalación y migración, `.env.example`, las pruebas y el script de integración.

## 11. Capturas de pantalla

No se han incorporado capturas gráficas. El menú de la sección 6 documenta la navegación y las pruebas verifican texto de PDF, QR decodificable y pantallas administrativas conectadas a servicios reales. Se pueden añadir capturas reales de consola y documentos de demostración antes de entregar.

## 12. Problemas conocidos y limitaciones

- La supresión con historial es lógica. No se borran individualmente líneas de una factura emitida ni se traslada la identidad de relaciones históricas.
- No hay autenticación ni roles; seleccionar el ID del profesor no acredita su identidad.
- No hay calendario académico de fechas por período, cargos mensuales automáticos ni historial de vigencias de tarifas particulares.
- Los reportes son internos a cada módulo, sin exportador general; algunos DAO devuelven texto y podrían migrar a DTO.
- La capacidad agregada y exclusividad entre tablas dependen del servicio y sus bloqueos; escrituras SQL directas pueden eludir el protocolo. La precisión original de notas tampoco queda garantizada únicamente por `DECIMAL(2,1)`.
- PDF, CUFE, QR, resolución y datos institucionales predeterminados son simulados. No hay firma, transmisión ni validación DIAN. La fuente estándar no cubre todo Unicode.
- El servicio produce una línea por abono; esquema/renderizador permiten varias. Hay recuperación, pero no atomicidad única entre MySQL y archivos. Los consecutivos pueden tener huecos por rollback.
- Docker utiliza `mysql:8.0` sin fijar digest. No se certifican rendimiento institucional, recuperación de backups ni seguridad de todas las dependencias.
- Shade puede informar metadatos duplicados; la compilación y arranque auditados son correctos.
- La licencia del código sigue pendiente de definición.

La auditoría acredita los escenarios revisados y ejecutados, no una garantía universal de ausencia de defectos ni el cierre de las mejoras futuras.

## 13. Futuras mejoras

Incorporar autenticación/roles, calendario académico, vigencias de tarifas y cargos recurrentes. Añadir DTO y exportación de reportes, paginación de consultas, coordinación de archivos entre procesos y comprobación de integridad de PDF. Definir backups, recuperación, pruebas de carga y revisión de dependencias antes de uso institucional.

Conservar los límites transaccionales y ampliar las pruebas para cada nuevo flujo. Para entrega académica, completar metadatos, verificar repositorio privado, agregar al instructor con lectura y registrar el SHA completo del commit final en `main` o `master`; usuario del instructor y plazo aún no han sido suministrados.
