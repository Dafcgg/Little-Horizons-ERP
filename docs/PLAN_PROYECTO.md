# Plan de implementación — Little Horizons ERP

Estado: código base implementado. La estructura de paquetes fue actualizada por la instrucción técnica posterior: conexión, errores y configuración se ubican en `util/`, sin paquetes raíz `config/` ni `exception/`. Véanse README y `docs/acceptance.md` para el alcance y la validación de esta entrega.

## 1. Base del plan

Se leyó completo `/home/camper/Descargas/proyecto_erp_little_horizons.md` y se revisó el [SQL oficial del anexo](https://drive.google.com/file/d/1fRaR8EUwcNheOWyMK5YJMkW0NWHo5JK7/view?usp=sharing), descargado para su inspección. El repositorio no tenía código ni documentación al comenzar.

El producto es una aplicación **Java de consola**, organizada en **MVC**, con persistencia en **MySQL**, para centralizar la gestión académica, extracurricular, financiera y de recursos del colegio.

Restricciones que guían todas las etapas:

- Código Java, identificadores, comentarios y mensajes de commit en inglés. Menús y documentación para el usuario en español. Tablas, columnas y comentarios SQL en español, conforme al anexo.
- Base de datos `erp_little_horizons`. MySQL 8.0.16 o superior dentro de la familia 8.x, para que las restricciones `CHECK` se apliquen efectivamente.
- JDBC con `PreparedStatement`, cierre mediante `try-with-resources` y transacciones para operaciones que modifican varias entidades.
- Notas `DECIMAL(2,1)`, entre 0.0 y 5.0. Importes monetarios con `BigDecimal` en Java y `DECIMAL` en SQL.
- Facturación PDF con CUFE, QR y resolución **simulados**, tal como pide el ejercicio. No se presentará este resultado como una integración real ni como una factura validada por la DIAN.
- Entrega completa: código, `docs/database.sql` con DDL y DML, README exigido, logs y generación de PDF verificadas.

## 2. Decisiones técnicas iniciales

| Elemento | Decisión propuesta | Motivo |
| --- | --- | --- |
| Lenguaje | Java 17 | Está disponible en el entorno y permite una aplicación Java estándar. |
| Construcción | Maven, estructura convencional `src/main/java` | Gestionar dependencias y ejecutar compilación y pruebas de forma reproducible. Maven también está instalado. |
| Persistencia | MySQL + JDBC + MySQL Connector/J | Cumplir el uso de SQL explícito y `PreparedStatement`. |
| Interfaz | Consola con un lector de entradas compartido | Validar, reintentar y volver atrás de manera consistente. |
| PDF | Apache PDFBox | Crear y comprobar el contenido del documento. |
| QR | ZXing | Codificar los datos de demostración de la factura. |
| Logs | `java.util.logging`, archivo `error_log.txt` | Cubrir el requisito sin una dependencia adicional. |
| Pruebas | JUnit 5 y pruebas de integración contra MySQL aislado | Verificar reglas de negocio, restricciones y transacciones con el motor real. |
| Configuración | Variables de entorno y archivo de ejemplo sin secretos | Separar credenciales, datos del colegio y rutas del código. |

Al iniciar la implementación se verificarán y fijarán versiones compatibles de las dependencias. La presencia del cliente MySQL y Docker está confirmada; aún falta verificar disponibilidad y versión del servidor. El IDE depende de la indicación del trainer.

## 3. Arquitectura y responsabilidades

```text
pom.xml
src/
  main/
    java/com/littlehorizons/erp/
      Main.java
      model/          Entidades y enumeraciones del dominio
      view/           Menús, lectura y presentación en consola
      controller/     Coordinación de acciones de la interfaz
      service/        Reglas de negocio y límites de transacción
      dao/            Contratos de acceso a datos
        jdbc/         Implementaciones JDBC y mapeo de columnas
      util/           Conexión, configuración, excepciones, PDF y logging
    resources/        Configuración de ejemplo y recursos PDF
  test/java/com/littlehorizons/erp/
docs/
  PLAN_PROYECTO.md
  database.sql        DDL + DML, separados claramente
  database-changes.md Justificación de ampliaciones al anexo
  acceptance.md       Escenarios y resultados de aceptación
invoices/             PDF generados; ignorados por Git
README.md
.gitignore
```

Flujo de una operación: **vista → controlador → servicio → DAO → MySQL**. La vista no ejecuta SQL y el DAO no solicita datos por consola. El servicio comparte una conexión dentro de cada transacción; un DAO no debe confirmar por su cuenta una operación compuesta.

Se aplicarán separación de responsabilidades e inversión de dependencias mediante interfaces donde aporten pruebas o sustitución real: DAO y generador de facturas. Se usarán lambdas y Stream API para procesamiento de colecciones; filtros y agregaciones grandes permanecerán en SQL. El README explicará SOLID, MVC, DAO y los patrones realmente implementados con referencias al código.

## 4. Ajustes necesarios sobre el esquema suministrado

El DDL del anexo será la base de `docs/database.sql`; cada cambio quedará justificado. No se ejecutará su `DROP DATABASE` sobre una base existente como parte del arranque normal. La reconstrucción de datos será una operación explícita para entornos de prueba.

| Hallazgo del anexo | Ajuste previsto |
| --- | --- |
| No hay tabla de horarios de clase. | Añadir días y franjas horarias vinculadas a asignaciones; comprobar solapamientos por profesor, año y grupo. |
| No hay valores de conceptos diferenciados por estudiante. | Añadir configuración de tarifas o descuentos por estudiante y vigencia. |
| Solo existen pagos; no se representan deudas ni vencimientos mensuales. | Añadir obligaciones/cargos con vencimiento y aplicación de pagos. La mora se obtiene del saldo pendiente, incluso cuando no existe ningún pago. |
| Las facturas solo enlazan un pago; no guardan líneas ni totales históricos. | Añadir detalle, totales y copia de los datos del emisor y responsable al emitir. Conservar el vínculo con el pago. |
| `facturas.id_pago` no es único y no existe estado de generación del PDF. | Evitar facturas duplicadas para un mismo pago y registrar generación pendiente, completada o fallida, con regeneración del mismo documento. |
| Las membresías permiten duplicar estudiantes activos en un club. | Garantizar una sola membresía activa por pareja, conservando historial de retiro y reingreso. |
| Parentesco y responsabilidad financiera están en el acudiente. | Representarlos por relación estudiante–acudiente, para contemplar responsabilidades distintas por estudiante. |
| Existen borrados en cascada de información histórica. | Revisar restricciones y usar retiro, baja o inactivación para entidades con historial académico, financiero o préstamos. |
| Grupo/sección puede ser nulo en asignaciones. | Definir una representación consistente de grupo y restricciones que eviten duplicados y asociaciones ambiguas. |

Se conservarán las tablas separadas `prestamos_estudiante` y `prestamos_profesor`. La exclusividad de un recurso se garantizará bloqueando su fila durante la transacción, independientemente del tipo de solicitante.

El comentario inicial del anexo menciona `little_horizons_erp`, pero su SQL y el documento principal usan `erp_little_horizons`: se utilizará este último de forma consistente.

## 5. Etapas y criterios de aceptación

### Etapa 1 — Base ejecutable y configuración

- Crear proyecto Maven, paquetes, punto de entrada y menú principal.
- Configurar conexión JDBC, variables de entorno, logging y manejo central de errores.
- Implementar lectura validada, cancelación y regreso a menús sin llamadas recursivas.
- Añadir `.gitignore` para credenciales, logs, compilados y PDF generados.

**Se acepta cuando:** compila, abre el menú, permite salir y comunica un fallo de conexión sin exponer secretos; el detalle técnico queda en `error_log.txt`.

### Etapa 2 — Modelo relacional y datos de demostración

- Integrar el DDL del anexo con los ajustes justificados y elaborar el esquema de relaciones.
- Separar DDL y DML en `docs/database.sql` y respetar orden de dependencias.
- Crear al menos **30 filas en cada una** de estas ocho tablas: `estudiantes`, `acudientes`, `profesores`, `materias`, `registros_academicos`, `pagos`, `clubes` y `recursos`.
- Poblar también tablas relacionadas con datos suficientes: matrículas, inscripciones, asignaciones, asistencia, membresías, eventos, obligaciones y préstamos.
- Utilizar datos sintéticos verosímiles para Colombia, con hermanos, becas, pagos parciales, mora, clubes llenos y préstamos vencidos. No utilizar información personal de estudiantes reales.

**Se acepta cuando:** el script carga desde cero en MySQL, respeta todas las restricciones, cumple los ocho mínimos y permite recorrer los casos de demostración.

### Etapa 3 — Estudiantes, acudientes y matrícula

- Registrar, consultar, editar y retirar/inactivar estudiantes y acudientes según corresponda.
- Exigir al menos un acudiente por estudiante, registrar contactos de emergencia y permitir hermanos asociados al mismo acudiente.
- Crear matrículas por grado y año, consultar historial y gestionar estados.
- Evitar documentos duplicados, fechas inválidas y matrículas repetidas del mismo estudiante y año.

**Se acepta cuando:** se registra un estudiante con acudiente y matrícula de forma coherente, se consulta su historial y no se puede dejar sin acudiente ni duplicar su matrícula anual.

### Etapa 4 — Profesores, materias y horarios

- Gestionar profesores y catálogo de materias por grado.
- Asignar profesores por materia, año y grupo; registrar días y horas.
- Inscribir estudiantes por materia y período, validando matrícula, grado y grupo.
- Detectar choques de horario del profesor y del grupo con intervalos de tiempo.

**Se acepta cuando:** se puede preparar un curso completo y se rechazan cruces horarios e inscripciones que no corresponden a la matrícula.

### Etapa 5 — Notas, promedios y asistencia

- Registrar y corregir una nota por estudiante, materia, año y período; validar profesor asignado e inscripción.
- Calcular promedio por materia, promedio del estudiante en el período y promedio del curso en cada materia.
- Consultar historial por estudiante y año.
- Registrar asistencia diaria por materia: presente, ausente, tardanza y excusa médica.
- Reportar patrones de inasistencia con rango de fechas y umbral configurable.

**Se acepta cuando:** se rechazan notas fuera de rango o con precisión inválida, no se duplican registros, los promedios coinciden con casos conocidos y se identifica un caso de más de tres ausencias en una semana.

### Etapa 6 — Actividades extracurriculares

- Gestionar clubes, responsables, cupos, altas y retiros de miembros.
- Validar cupos dentro de una transacción para impedir sobreinscripción concurrente.
- Gestionar calendario de eventos por tipo y registrar participantes sin duplicados.
- Registrar posiciones únicamente en eventos con competencia.

**Se acepta cuando:** no se supera el cupo de un club, un retiro libera plaza y un evento muestra participantes y resultados válidos.

### Etapa 7 — Finanzas, pagos y factura PDF

- Gestionar conceptos, tarifas particulares, becas y descuentos.
- Crear obligaciones con fecha de vencimiento y registrar pagos con recibo único, método y responsable asociado al estudiante.
- Aplicar pagos a obligaciones y calcular saldos y mora por estudiante o acudiente.
- Consultar historial financiero individual y familiar.
- Reservar un consecutivo único y guardar datos de factura al confirmar el pago.
- Generar PDF con datos del colegio y acudiente, conceptos, cantidades, precios, subtotales, IVA cuando esté configurado, total, CUFE simulado, QR y resolución simulada.
- Guardar como `invoices/factura_000123.pdf` y mostrar la ubicación.
- Si falla la escritura del PDF, conservar el pago confirmado y permitir regenerar la misma factura sin duplicar pago ni consecutivo. La transacción SQL no cubre la escritura de archivos.

**Se acepta cuando:** descuentos y pagos parciales producen saldos exactos, se detecta una obligación vencida sin pagos, se genera un PDF legible y un reintento tras fallo no duplica registros. El documento identifica su naturaleza de demostración.

### Etapa 8 — Recursos y préstamos

- Gestionar inventario por código único, categoría, ubicación y estado.
- Prestar a estudiantes y profesores utilizando las tablas separadas del anexo.
- Validar disponibilidad y fechas; actualizar recurso y préstamo en una transacción.
- Registrar devoluciones y recuperar disponibilidad.
- Consultar vencidos según fecha esperada y ausencia de devolución, sin depender de un proceso manual que cambie el estado.

**Se acepta cuando:** un recurso no puede prestarse dos veces, ni siquiera entre tipos distintos de solicitantes; su devolución habilita un nuevo préstamo y los vencidos se reportan correctamente.

### Etapa 9 — Reportes, integración y entrega

- Reunir en el menú de reportes rendimiento por estudiante/curso, inasistencias, participación extracurricular, mora, historial familiar y préstamos vencidos.
- Ejecutar pruebas integradas y recorrido completo desde matrícula hasta factura y devolución.
- Completar README con las 16 secciones indicadas en el enunciado, marcando como opcionales las que así se definieron.
- Documentar instalación, driver JDBC, configuración, compilación, ejecución, logs, limitaciones y ejemplos de SOLID y patrones.
- Preparar checklist y evidencias reproducibles en `docs/acceptance.md`.

**Se acepta cuando:** una instalación limpia permite cargar datos, compilar, ejecutar y demostrar todos los módulos con la documentación suministrada.

## 6. Reglas propuestas para los puntos no definidos

Estas reglas son supuestos iniciales y deben quedar documentadas antes de implementar el cálculo correspondiente:

- **Promedios:** pesos iguales para los cuatro períodos y para las materias, salvo indicación del trainer. No convertir notas faltantes en cero; mostrar promedios parciales como tales. La nota `Final` será derivada de I–IV y no se volverá a contar como un quinto período. Redondeo explícito a un decimal con `HALF_UP`.
- **Asistencia:** umbral inicial de más de tres registros `Ausente` entre lunes y domingo. Las excusas médicas y tardanzas se informan por separado. Aclarar si el colegio quiere contar días distintos en lugar de registros por materia.
- **Mora:** saldo positivo cuya fecha de vencimiento sea anterior en más de un mes calendario a la fecha de consulta. Mostrar vencimiento y saldo; mantener configurable el criterio si se requiere usar 30 días.
- **Calendario financiero:** no inferir mensualidades a partir de pagos. Definir explícitamente los meses facturables, fechas de vencimiento y conceptos asignados; generar cargos recurrentes sin duplicados por estudiante, concepto y período de cobro.
- **Importes:** pesos colombianos, dos decimales, sin cálculos con `double`. Descuentos no pueden producir obligaciones negativas ni aplicarse dos veces.
- **Datos históricos:** el cambio posterior de tarifa, nombre o dirección no modifica una factura emitida. Los registros relacionados se conservan mediante estados de retiro o inactivación.
- **Ámbito inicial:** consola local con selección de profesor para las operaciones académicas y validación de su asignación. Autenticación, roles, interfaz web, pagos en línea y conexión real con DIAN quedan como posibles mejoras fuera del enunciado.

## 7. Verificación prevista

Las pruebas se concentrarán en reglas y operaciones con riesgo de inconsistencia:

| Área | Casos esenciales |
| --- | --- |
| Académico | Límites 0.0/5.0, precisión, inscripción requerida, promedios parciales/finales, choques horarios. |
| Integridad | Documentos, matrículas, notas, asistencias, recibos y participaciones duplicados; FK y `CHECK`. |
| Clubes | Última plaza, sobrecupo, membresía repetida y retiro/reingreso. |
| Finanzas | Becas, deuda sin pagos, abonos parciales, frontera de mora, consecutivos y reintento de PDF. |
| Préstamos | Exclusividad entre las dos tablas, devolución, vencimiento y rollback ante error. |
| PDF | Archivo existente, contenido esperado, totales, identificación de simulación y QR decodificable. |
| Operación | Entradas inválidas, cancelación, conexión caída, escritura fallida y generación de logs. |

Se ejecutarán pruebas al completar cada módulo, y un recorrido de aceptación conjunto al final. Las pruebas de integración utilizarán una base aislada; no borrarán datos de uso normal.

## 8. Orden de trabajo y entrega

Dependencias: **1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9**. Una vez completadas las bases compartidas, extracurricular y recursos pueden desarrollarse de manera independiente; este plan propone ejecución secuencial para mantener entregas pequeñas y comprobables.

Cada etapa terminará con un resultado ejecutable, verificaciones pertinentes y un commit atómico con mensaje en inglés, cuando se realicen los commits del proyecto. No se fija un calendario ficticio: faltan fecha límite, integrantes y disponibilidad.

Datos pendientes que no impiden preparar la estructura:

- Nombres, correos y contactos de autores; IDE indicado por el trainer.
- Fecha y hora límite, URL del repositorio y usuario del instructor.
- Datos institucionales de demostración para el PDF y reglas de evaluación, mora y calendario de cobro si difieren de los supuestos.
- Licencia acordada y condiciones de uso de la entrega.

Antes de entregar: comprobar que el repositorio de GitHub es privado, dar acceso de lectura al instructor indicado, verificar que el commit final está en `main` o `master` y obtener su SHA completo. No se incorporarán identidades, contactos ni datos institucionales inventados como si fueran reales.

**Estado de la fase CRUD:** edición y administración implementadas; véase `docs/crud_matrix.md`. Continúan como mejoras las vigencias de tarifas, calendario automático, autenticación y preparación de la entrega GitHub.
