## 1. Título del Proyecto

**ERP Colegio "Little Horizons"**

Aplicación de consola para la gestión del Colegio Internacional Little Horizons, desarrollada en Java con arquitectura MVC, servicios transaccionales y persistencia JDBC.

## 2. Descripción

El ERP centraliza los registros académicos, las actividades extracurriculares, los pagos y el inventario. Resuelve la dispersión de información, los cruces de horarios, los sobrecupos de clubes, los pagos duplicados y los préstamos simultáneos de un mismo recurso.

Incluye operaciones de creación, consulta, edición y baja para las 26 entidades del esquema, reportes de rendimiento, inasistencias, morosidad y préstamos vencidos, y facturas PDF con información DIAN **simulada**. Las bajas conservan el historial cuando corresponde; las correcciones financieras anulan el original y crean un reemplazo trazable. Los identificadores y comentarios Java y las pruebas están en inglés; la interfaz y el esquema SQL están en español.

## 3. Autores

- **Dilan Andrés Fonseca Téllez** — desarrollador del proyecto.
- **Correo electrónico:** [espacio reservado para el correo del autor].
- **Perfil:** [Dafcgg en GitHub](https://github.com/Dafcgg).

## 4. Tecnologías Utilizadas

| Tecnología | Versión / configuración | Responsabilidad |
| --- | --- | --- |
| Java | 17, `maven.compiler.release=17` | Records, enums, Stream API, JDBC y `BigDecimal`. |
| Maven | 3.8+ | Dependencias, compilación, pruebas y empaquetado. |
| IntelliJ IDEA / VS Code | Con soporte Java y Maven | Edición y depuración; no son necesarios para ejecutar el JAR. |
| MySQL | 8.0+; mínimo efectivo 8.0.16 por los `CHECK` | Persistencia InnoDB, claves foráneas y bloqueos. |
| MySQL Connector/J | 26.10.0 | Driver JDBC, dependencia `com.mysql:mysql-connector-j`. |
| Apache PDFBox | 3.0.8 | Escritura y lectura de facturas PDF. |
| ZXing | 3.5.4 (`core` y `javase`) | Generación y validación de códigos QR. |
| JUnit Jupiter | 5.11.4 | Pruebas unitarias y de integración. |
| `java.util.logging` | Incluido en Java | Registro de errores con `AppLogger`. |

Las versiones de dependencias proceden de [pom.xml](pom.xml). Maven Shade genera un JAR con dependencias, conserva el proveedor JDBC y define `Main` como punto de entrada. La codificación de fuentes es UTF-8.

## 5. Requerimientos Previos

- **JDK 17 o superior** y **Maven 3.8 o superior**. Comprobar con `java -version` y `mvn -version`.
- **MySQL 8.0 o superior**. Usar como mínimo **8.0.16** para que se apliquen las restricciones `CHECK` del esquema.
- Git, acceso a Maven Central y una cuenta MySQL con permisos sobre la base del ERP.
- Permiso de escritura para `error_log.txt` y el directorio de facturas.
- Para el script de integración aislada: Bash y Docker; en Windows puede utilizarse un entorno que disponga de ambos.

Se recomienda ejecutar con la zona horaria `America/Bogota`. Las pruebas de integración necesitan una base desechable: insertan y modifican datos.

## 6. Configuración de la Base de Datos

### 6.1 Creación de `erp_little_horizons` e importación de `docs/database.sql`

El archivo [docs/database.sql](docs/database.sql) crea la base `erp_little_horizons`, las 26 tablas y sus relaciones. Separa DDL y DML y carga al menos 30 registros por tabla, incluidos **30 clubes y 30 eventos con descripciones en español**. Los datos son sintéticos y las fechas de demostración pertenecen a 2026.

Desde la raíz del repositorio, sobre un servidor sin la base del proyecto:

```bash
mysql -u root -p --default-character-set=utf8mb4 < docs/database.sql
```

En PowerShell, puede importarse desde el cliente MySQL para preservar la codificación:

```text
mysql -u root -p --default-character-set=utf8mb4
mysql> SOURCE C:/ruta/Little-Horizons-ERP/docs/database.sql;
```

El script ya contiene `CREATE DATABASE erp_little_horizons`; no cree primero una base con ese nombre. No elimina bases ni se ejecuta al iniciar Java. Es una instalación inicial, no una migración idempotente: se detiene si la base ya existe. El DML se carga en una transacción; el DDL requiere revisar una instalación parcial antes de repetirla.

Para instalaciones de la fase anterior al CRUD completo existe [docs/migrations/002_complete_crud.sql](docs/migrations/002_complete_crud.sql). Aplíquelo una sola vez a esa versión; no lo aplique después del script completo. Las columnas `descripcion` ya existían en el esquema: esta revisión conecta su uso en Java y completa las semillas.

### 6.2 Variables de entorno / credenciales sin secretos quemados

Cree una cuenta de aplicación con una contraseña propia:

```sql
CREATE USER 'little_horizons'@'localhost' IDENTIFIED BY 'REEMPLAZAR_POR_CLAVE_PROPIA';
GRANT SELECT, INSERT, UPDATE, DELETE
ON erp_little_horizons.* TO 'little_horizons'@'localhost';
```

Configuración Bash, solicitando la contraseña sin incluirla en el comando:

```bash
export ERP_DB_URL='jdbc:mysql://localhost:3306/erp_little_horizons?serverTimezone=America/Bogota'
export ERP_DB_USER='little_horizons'
read -rs -p 'Contraseña MySQL: ' ERP_DB_PASSWORD
export ERP_DB_PASSWORD
export ERP_INVOICE_DIR='invoices'
```

Configuración PowerShell:

```powershell
$env:ERP_DB_URL='jdbc:mysql://localhost:3306/erp_little_horizons?serverTimezone=America/Bogota'
$env:ERP_DB_USER='little_horizons'
$credential=Get-Credential -UserName 'little_horizons' -Message 'Credenciales MySQL del ERP'
$env:ERP_DB_PASSWORD=$credential.GetNetworkCredential().Password
$env:ERP_INVOICE_DIR='invoices'
```

| Variable | Comportamiento |
| --- | --- |
| `ERP_DB_URL` | MySQL local, base `erp_little_horizons` y zona `America/Bogota` por defecto. |
| `ERP_DB_USER` | `little_horizons` por defecto. |
| `ERP_DB_PASSWORD` | Obligatoria al abrir una conexión; no tiene contraseña predeterminada. |
| `ERP_INVOICE_DIR` | `invoices` por defecto, relativo al directorio de ejecución. |
| `ERP_SCHOOL_NAME` / `ERP_SCHOOL_NIT` | Emisor e identificación; valores predeterminados marcados como DEMO/SIMULADO. |
| `ERP_SCHOOL_ADDRESS` / `ERP_SCHOOL_PHONE` / `ERP_SCHOOL_EMAIL` | Datos configurables del emisor; el correo de demostración usa `example.invalid`. |

[.env.example](.env.example) documenta las variables; la aplicación **no carga `.env` automáticamente**. No incluya credenciales reales en el repositorio. `.gitignore` excluye `.env` y sus variantes locales.

### 6.3 Driver JDBC MySQL Connector/J

Maven descarga el driver mediante esta dependencia real de `pom.xml`:

```xml
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <version>26.10.0</version>
</dependency>
```

JDBC descubre el proveedor automáticamente y Shade conserva su registro en el JAR. No se necesita copiar un driver al IDE ni llamar a `Class.forName`.

## 7. Instalación y Ejecución

### 7.1 Clonar repositorio

```bash
git clone https://github.com/Dafcgg/Little-Horizons-ERP.git
cd Little-Horizons-ERP
```

### 7.2 Abrir en IDE

En IntelliJ IDEA, abra `pom.xml` como proyecto Maven y seleccione un JDK 17 o superior. En VS Code, abra la carpeta con las extensiones Java y Maven instaladas. Configure las variables de entorno en la terminal o en la ejecución local del IDE.

### 7.3 Compilar con `mvn clean package`

```bash
mvn clean package
```

Para verificar también la fase `verify`:

```bash
mvn clean verify
```

Para ejecutar toda la integración con una base MySQL desechable:

```bash
./scripts/test-integration.sh
```

El script levanta un contenedor MySQL 8.0 en un puerto local dinámico, espera una consulta válida, importa el SQL, ejecuta `mvn clean verify` y elimina el contenedor al terminar. Sus credenciales pertenecen únicamente a ese entorno temporal. Configura `ERP_TEST_DB_URL`, `ERP_TEST_DB_USER` y `ERP_TEST_DB_PASSWORD`, y las variables `ERP_DB_*` equivalentes para verificar las conexiones independientes del Singleton.

También puede preparar manualmente una base MySQL aislada e indicar esas seis variables. Sin `ERP_TEST_DB_URL` se omiten las pruebas de integración; sin `ERP_DB_URL` se omite la prueba que abre conexiones reales del Singleton. Por ello, una ejecución sin base no acredita la suite completa. Los informes se guardan en `target/surefire-reports/`.

Verificación completa del 15 de noviembre de 2026: **80 pruebas aprobadas (62 anteriores + 18 nuevas), 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS con `mvn clean verify`**. Ejecutada con Maven 3.9.9, JDK 21 compilando bytecode Java 17 y MySQL 8.0.46 aislado. Incluye regresiones de PDF, descripciones, préstamos, Singleton, formularios y filtros de reportes. Los nombres únicos del escenario administrativo permiten repetirlo sobre la misma base de pruebas.

### 7.4 Ejecutar JAR

Después de importar la base y configurar las variables:

```bash
java -Duser.timezone=America/Bogota -jar target/little-horizons-erp-1.0.0-SNAPSHOT.jar
```

Alternativa de desarrollo: `mvn exec:java`. El menú abre sin conectar a MySQL; cada operación obtiene una conexión cuando la necesita. `0` vuelve o sale, `q` cancela y Enter conserva el valor actual en los formularios de edición. Use fechas `AAAA-MM-DD`, horas `HH:MM` e importes con punto o coma decimal.

## 8. Estructura del Proyecto

```text
Little-Horizons-ERP/
├── pom.xml
├── README.md
├── LICENSE
├── .gitignore
├── .env.example
├── docs/
│   ├── database.sql
│   └── migrations/002_complete_crud.sql
├── scripts/test-integration.sh
└── src/
    ├── main/java/com/littlehorizons/erp/
    │   ├── Main.java
    │   ├── model/
    │   ├── view/
    │   ├── controller/
    │   ├── service/
    │   ├── dao/
    │   │   └── jdbc/
    │   └── util/
    └── test/java/com/littlehorizons/erp/
```

| Capa | Responsabilidad y clases reales |
| --- | --- |
| Modelo | Records y enums como `Club`, `SchoolEvent`, `Invoice`, `LoanEntry` y `AbsenceSummary`. |
| Vista | `ConsoleView` lee entradas; `ConsoleFormatter` presenta los datos en español. |
| Controlador | `AcademicController`, `ActivityController`, `FinanceController`, `ResourceController` y controladores administrativos coordinan los formularios. |
| Servicio | Reglas de negocio y reportes en `AcademicService`, `ActivityService`, `FinanceService` y `ResourceService`. `TransactionalService` delimita las transacciones. |
| DAO | Interfaces de persistencia; implementaciones `Jdbc*Dao` con SQL estático parametrizado. `Jdbc` gestiona sentencias y resultados. |
| Utilidades | `DatabaseConnection`, `ConnectionProvider`, `AppLogger`, `BusinessException`, `InvoiceRenderer` y `PdfInvoiceGenerator`. |
| Composición | `Main` construye e inyecta dependencias y abre el menú. |

El flujo es vista → controlador → servicio → DAO → MySQL. El servicio abre/cierra la conexión y confirma o revierte la transacción; los DAO reciben esa conexión y no la cierran ni confirman. No hay SQL en las vistas ni lectura de consola en los DAO.

## 9. Principios y Patrones de Diseño Aplicados

Los fragmentos siguientes proceden de las clases actuales del repositorio; los fragmentos parciales se explican con su clase de origen.

### 9.1 Principios SOLID

**SRP — responsabilidad única.** `TransactionalService` controla la transacción, `PdfInvoiceGenerator` escribe el documento y `AppLogger` registra errores. Por ejemplo, el registro se concentra en este método real de `AppLogger`:

```java
public static void error(String operation, Throwable error) {
  LOGGER.log(Level.SEVERE, operation, error);
}
```

**OCP — abierto/cerrado.** La generación de facturas admite otra estrategia sin modificar los cálculos ni los pagos de `FinanceService`. Contrato real de `InvoiceRenderer`:

```java
@FunctionalInterface
public interface InvoiceRenderer {
  Path render(Invoice invoice) throws IOException;
}
```

**LSP — sustitución de implementaciones.** El servicio trabaja con proveedores que respetan el contrato de abrir una conexión utilizable por cada transacción. Puede recibir el Singleton o un proveedor JDBC para pruebas; ambos cumplen `ConnectionProvider`. No se comparte una conexión cerrada entre operaciones:

```java
@FunctionalInterface
public interface ConnectionProvider {
  Connection open() throws SQLException;
}
```

La suite verifica que cerrar una conexión del Singleton no cierra otra. Las estrategias de `InvoiceRenderer` también respetan su contrato: una implementación puede devolver la ruta o lanzar `IOException`, que el servicio maneja sin repetir el pago.

**ISP — segregación de interfaces.** Los contratos se separan por módulo: `StudentDao`, `AcademicDao`, `ActivityDao`, `FinanceDao`, `ResourceDao` y `CatalogDao`. Un servicio de actividades no depende de métodos financieros. Fragmento real de `ActivityDao`:

```java
List<Club> clubs(Connection c) throws SQLException;

Optional<Club> lockClub(Connection c, long club) throws SQLException;

int activeMembers(Connection c, long club) throws SQLException;
```

**DIP — inversión de dependencias.** `FinanceService` depende de interfaces y recibe sus colaboradores por constructor, sin crear un DAO ni un renderizador concreto:

```java
public FinanceService(
    ConnectionProvider connections,
    FinanceDao dao,
    InvoiceRenderer renderer,
    SchoolInformation school) {
  super(connections);
  this.dao = dao;
  this.renderer = renderer;
  this.school = school;
}
```

### 9.2 Patrones de Diseño

**Singleton.** `DatabaseConnection` tiene constructor privado e instancia estática única. La inicialización de clase de Java proporciona publicación segura entre hilos. Fragmento real:

```java
private static final DatabaseConnection INSTANCE = new DatabaseConnection();

private DatabaseConnection() {}

public static DatabaseConnection getInstance() {
  return INSTANCE;
}
```

`Main` lo obtiene con `var connections = DatabaseConnection.getInstance();`. Es Singleton del **proveedor**, no de `java.sql.Connection`: cada `open()` llama a `DriverManager.getConnection(...)`. Así se conserva el aislamiento de `TransactionalService`.

**DAO.** Los `Jdbc*Dao` encapsulan SQL y mapeo. Por ejemplo, `JdbcActivityDao` consulta clubes y recupera también `descripcion`; las interfaces exponen modelos en lugar de `ResultSet`. El helper real `Jdbc` enlaza los parámetros:

```java
for (int index = 0; index < values.length; index++)
  statement.setObject(index + 1, values[index]);
```

Las variantes de tabla y bloqueo seleccionan sentencias completas predefinidas. No se concatenan cadenas para construir SQL en `src/main`.

**Inyección de dependencias por constructor.** `Main` compone el módulo académico con esta línea real:

```java
var academicService = new AcademicService(connections, new JdbcAcademicDao());
```

`AcademicService` conserva el contrato recibido:

```java
public AcademicService(ConnectionProvider connections, AcademicDao dao) {
  super(connections);
  this.dao = dao;
}
```

**Strategy.** `InvoiceRenderer` define la estrategia y `PdfInvoiceGenerator` la implementación PDF. El constructor real del generador normaliza su destino:

```java
public PdfInvoiceGenerator(Path directory) {
  this.directory = directory.toAbsolutePath().normalize();
}
```

La prueba `FinancePdfRegenerationIntegrationTest` sustituye esa estrategia por esta lambda real para reproducir un fallo de escritura:

```java
invoice -> {
  throw new IOException("Output file is locked");
}
```

Los reportes usan Stream API explícitamente en la capa de servicio. Por ejemplo, en `AcademicService.absences`:

```java
return transaction(c -> dao.absenceCounts(c, start, end)).stream()
    .filter(summary -> summary.absentClasses() > threshold)
    .map(
        summary ->
            "Estudiante %d: %d clases ausentes acumuladas"
                .formatted(summary.studentId(), summary.absentClasses()))
    .toList();
```

`FinanceService.overdue` filtra saldos positivos con vencimiento anterior a un mes calendario; `ResourceService.overdue` filtra préstamos abiertos vencidos y los transforma a `Loan` mediante `map`.

## 10. Funcionalidades Principales

**Académico.** Gestión de estudiantes, acudientes, grados, profesores, materias, matrículas, asignaciones, horarios e inscripciones. Protección del último acudiente activo y detección de cruces de profesor/grupo. Notas de 0.0 a 5.0 con máximo un decimal; promedios con pesos iguales y `HALF_UP`. El cierre exige los cuatro períodos y se invalida al corregir una nota. Asistencia con presencia, ausencia, tardanza y excusa médica. El reporte aplica un umbral estricto: con 3, muestra **más de tres clases ausentes acumuladas** en el rango, incluso si corresponden a distintas materias el mismo día; no cuenta días distintos ni tardanzas/excusas como ausencias.

**Extracurricular.** Clubes y eventos con nombre y descripción en creación, consulta, listado y edición. Asignación de profesor, control de cupos, membresías y retiro/reactivación conservando historial. La inactivación del club retira sus miembros activos. Eventos con fecha, lugar, tipo, carácter competitivo y participantes; las posiciones solo se permiten en competencias. La descripción se conserva al desactivar o reactivar.

**Financiero / Facturación DIAN simulada.** Conceptos, tarifas particulares, obligaciones, pagos parciales, recibos únicos, saldos e historial individual/familiar. Cálculos con `BigDecimal`, validación de IVA y máximo dos decimales. Bloqueos para evitar sobrepagos; correcciones y anulaciones trazables. El reporte de morosos exige saldo positivo y vencimiento de más de un mes calendario. Los PDF conservan emisor, acudiente e importes históricos, incluyen CUFE SHA-256, QR y resolución simulados. Un fallo de generación conserva el pago y marca `Fallida`, también desde `Generada`; reintentar correctamente restaura `Generada` sin duplicar el pago ni la factura.

**Recursos.** Categorías, inventario, ubicación, mantenimiento y bajas. Préstamos separados para estudiantes y profesores con bloqueo común del recurso, consulta individual/listados, cambio de vencimiento, devolución y anulación. Si un préstamo almacenado como `Activo` tiene fecha esperada anterior a hoy, las consultas exponen **`Vencido`** sin modificar la fila; un vencimiento de hoy sigue activo. Se pueden devolver préstamos activos o vencidos, liberando el recurso. Los devueltos y anulados no se reclasifican por fecha.

## 11. Manejo de Errores y Logs

`BusinessException` expresa validaciones recuperables con mensajes en español. `AppLogger` registra las causas técnicas mediante `java.util.logging` en `error_log.txt`, con UTF-8, límite aproximado de 1 MB y un archivo de rotación. Si no puede abrirse el log al iniciar, `Main` informa y termina. Los fallos deliberados de pruebas pueden producir entradas de error esperadas sin representar fallos de la suite.

`TransactionalService` configura `READ_COMMITTED`, desactiva autocommit, ejecuta commit al completar y rollback ante error SQL, excepción de ejecución o `Error`. Si el rollback también falla, esa causa queda suprimida en la excepción original. Conexiones, sentencias, resultados y documentos se cierran con `try-with-resources`.

El pago y la factura se confirman **antes** de escribir el PDF. Una escritura fallida no repite la transacción financiera: registra el fallo y guarda `Fallida`. Si esa actualización afecta cero filas o no puede persistirse, el servicio registra el problema y lanza `BusinessException`, indicando que se regenere la factura y no se registre otro pago. El marcado exitoso también verifica las filas actualizadas y el estado de negocio de la factura.

El PDF usa un archivo temporal y movimiento atómico cuando el sistema lo permite. MySQL y el sistema de archivos no comparten una transacción distribuida; si falla MySQL tras escribir el archivo puede requerirse regeneración. Una copia anterior puede conservarse aunque el estado sea `Fallida`; el estado refleja el último intento de generación.

`.gitignore` excluye explícitamente `*.class`, `error_log.txt`, `*.log`, `invoices/*.pdf`, `facturas/*.pdf` y `.env`, además de `target/`, temporales y ajustes locales del editor. Los PDF y logs de comprobación no forman parte de la entrega versionada.

## 12. Capturas de Pantalla

Las siguientes capturas de texto reproducen la consola; no requieren una interfaz gráfica. Los identificadores y fechas dependen de la base utilizada.

Menú principal:

```text
=== ERP Colegio Little Horizons ===
1. Estudiantes, acudientes y matrícula
2. Académico, profesores y asistencia
3. Clubes y eventos
4. Finanzas y facturación
5. Inventario y préstamos
6. Administración, edición e inactivación
0. Volver / salir
Opción (q: cancelar):
```

Reportes capturados el 15 de noviembre de 2026 sobre la base aislada de verificación, después de ejecutar los escenarios de integración:

```text
MOROSOS
Obligación #31 | Estudiante: 1 | Matrícula | Vencimiento: 2026-02-20 | Saldo COP 300000.00
INASISTENCIAS: 2026-02-02 a 2026-02-08, umbral 3
Estudiante 56: 4 clases ausentes acumuladas
PRESTAMOS VENCIDOS
Préstamo #34 | Tipo: estudiante | Solicitante: 1 | Recurso: 35 | Vencimiento: 2026-11-14
```

Texto extraído con PDFBox de `invoices/factura_000001.pdf`, generado por `PdfInvoiceGenerator` a partir de la factura de demostración de la semilla:

```text
Colegio Internacional Little Horizons S.A.S. (DEMO)
NIT: 900000000-0 (SIMULADO)
Calle de demostracion 123, Bogota
0000000000 | colegio@example.invalid
FACTURA DE DEMOSTRACION - SIN VALIDACION DIAN
Resolucion SIMULADA DEMO-001
Factura: LH-000001 | Fecha: 2026-01-15
Responsable: Daniela García
Documento: 50000000
Direccion: Calle 10 # 20-30

Concepto | Cantidad | Valor unitario | Subtotal
Matrícula | 1 | COP 350000.00 | COP 350000.00

Subtotal: COP 350000.00
IVA: COP 0.00
TOTAL: COP 350000.00
CUFE SIMULADO: a6267b0f49ca2d33393b468e5b01bcbf210c3eb2b40620f793ed9adcf50fafc4
```

El documento coloca el encabezado y los datos del responsable en la parte superior, los conceptos e importes debajo y un QR en la zona inferior izquierda de la última página. El QR es una imagen y no aparece en la extracción textual. Las pruebas comprueban el texto, la paginación y la decodificación del QR. El PDF generado queda excluido de Git.

## 13. Problemas Conocidos y Limitaciones

- No hay autenticación ni roles; seleccionar el ID de un profesor no acredita su identidad.
- No existe calendario académico por período, generación mensual automática de cargos ni historial de vigencia de tarifas.
- Las bajas con historial son lógicas. Las líneas de facturas emitidas se conservan y las correcciones crean documentos reemplazantes.
- CUFE, QR, resolución y emisor predeterminado son simulados: no hay firma, transmisión ni validación DIAN.
- El PDF utiliza una fuente estándar que sustituye caracteres no admitidos; los reportes no tienen exportador general.
- La exclusividad de préstamos y los cupos dependen de los bloqueos de los servicios. Una escritura SQL directa puede eludir esas reglas.
- La generación PDF se coordina dentro de la JVM. Varias JVM compartiendo directorio requieren coordinación adicional; tampoco existe atomicidad única entre base de datos y archivos.
- Las semillas tienen fechas de 2026 y los reportes dependen de la fecha local. El script SQL inicial no es idempotente.
- Shade puede advertir sobre metadatos duplicados de dependencias. Los consecutivos pueden tener huecos después de rollback.

## 14. Futuras Mejoras

Incorporar autenticación y roles, calendario escolar, cargos periódicos y vigencias de tarifas. Añadir exportación y paginación de reportes, una interfaz web y coordinación de PDF entre procesos. Automatizar copias de seguridad y recuperación, revisión de dependencias e integración continua contra MySQL. Ampliar pruebas de concurrencia y rendimiento conforme crezca el uso institucional, conservando las fronteras transaccionales.

## 15. Licencia

El proyecto se distribuye bajo **MIT License**. El texto completo, con atribución a Dilan Andrés Fonseca Téllez, está en [LICENSE](LICENSE). Permite usar, copiar, modificar y distribuir el software conservando el aviso de copyright y la licencia, sin garantía.

## 16. Contacto

- **Autor y desarrollador:** Dilan Andrés Fonseca Téllez.
- **Correo:** [espacio reservado para el correo del autor].
- **GitHub:** [Dafcgg](https://github.com/Dafcgg).
- **Repositorio:** [Little-Horizons-ERP](https://github.com/Dafcgg/Little-Horizons-ERP).
- **Incidencias:** [Issues del proyecto](https://github.com/Dafcgg/Little-Horizons-ERP/issues).
