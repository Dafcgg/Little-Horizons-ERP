# QA — Casos límite y transacciones concurrentes

Estado actual: CRUD completado y entrega Git preparada en modalidad simulada; véase [entrega_final.md](entrega_final.md). Las casillas siguientes se conservan como evidencia histórica del estado anterior.

Reporte histórico previo al cierre CRUD. La fase posterior completó ese pendiente y elevó la suite a 61 pruebas; véase [crud_matrix.md](crud_matrix.md). El checklist siguiente conserva el estado observado en esta ejecución original.

Fecha: 10 de noviembre de 2026. Pruebas ejecutadas sobre Java 17 y un MySQL 8 aislado mediante `scripts/test-integration.sh`. Se añadieron `StressScenariosTest` y `MavenStructureTest` a las suites existentes.

## Resultado

**41 pruebas aprobadas; 0 fallos, 0 errores y 0 omitidas.** El proceso de Maven terminó con `BUILD SUCCESS` y produjo el JAR ejecutable.

Las solicitudes concurrentes usan conexiones independientes y una barrera de inicio. Los rechazos se aceptan únicamente si corresponden al mensaje de negocio previsto; cualquier error SQL inesperado, timeout o fallo de hilo hace fallar la prueba.

| Caso | Carga / entrada | Resultado observado |
| --- | --- | --- |
| Cupos de clubes | 5 rondas de 20 estudiantes distintos; 3 plazas por club. | 100 intentos: 15 confirmados y 85 rechazados por falta de cupo. Cada club terminó con exactamente 3 membresías activas. |
| Préstamo compartido | 5 rondas de 20 solicitantes, mitad estudiantes y mitad profesores; un recurso por ronda. | 100 intentos: 5 confirmados y 95 rechazados por indisponibilidad. La suma de préstamos activos de ambas tablas fue exactamente 1 por recurso. |
| Notas | `5.5`, `-0.1`, `3.45`; límites válidos `0.0` y `5.0`. | Java rechazó los tres valores inválidos y aceptó los límites. MySQL rechazó `5.5` mediante `CHECK`. |
| Escritura de factura | Destino PDF ocupado por un directorio no vacío; fallo real al reemplazarlo. | El pago y la factura persistieron. El reintento conservó IDs, número y CUFE y generó un único PDF sin otro pago. |

Los contenedores y sus volúmenes se eliminan al terminar. La creación de `erp_little_horizons` se confirmó en ese entorno de prueba; no se certifica una instalación permanente en otro servidor.

## 1. Inscripciones concurrentes en clubes

`ActivityService.join` abre una transacción, bloquea la fila del club con `SELECT ... FOR UPDATE`, verifica membresía y cuenta integrantes activos. Solo inserta si el número es menor que `capacidad_maxima`.

InnoDB hace esperar a quienes compiten por esa misma fila. Después del commit del ganador, la siguiente operación observa el cupo actualizado. El índice único de membresía activa evita además duplicar al mismo estudiante en un club.

El límite agregado depende de esta operación de servicio y del protocolo de bloqueo; el `CHECK` del club solo exige que su capacidad sea positiva. No existe un `CHECK` que cuente filas de membresías ante un `INSERT` SQL arbitrario.

## 2. Notas fuera de rango y precisión inválida

`AcademicService.validateGrade` comprueba valor no nulo, rango de 0.0 a 5.0 y escala significativa máxima de un decimal. `3.45` se rechaza antes de persistir. Ceros decimales adicionales sin significado, como `4.00`, no alteran el valor y son aceptados.

MySQL almacena `valor_nota DECIMAL(2,1) NOT NULL` con `CHECK (valor_nota >= 0.0 AND valor_nota <= 5.0)`. El intento directo de guardar `5.5` produjo una excepción SQL y no modificó la nota anterior.

**Límite comprobado:** `DECIMAL(2,1)` no rechaza necesariamente la precisión de la entrada original. Al ejecutar directamente SQL con `3.45`, MySQL almacenó `3.5` por redondeo. La prueba verificó esa conducta y revirtió su transacción. La precisión se exige en el servicio; no debe atribuirse a ese `CHECK` una protección que no ofrece.

## 3. Fallo de disco y recuperación de factura

La prueba unitaria existente usa exactamente `factura_000123.pdf` como destino bloqueado. La nueva prueba integrada bloquea el nombre real de la factura asignada por MySQL dentro de un directorio temporal `invoices/`.

Secuencia observada:

1. Se crea una obligación y se confirma un pago de COP 100.00.
2. MySQL confirma pago, cabecera y detalle de factura antes de renderizar.
3. El generador escribe un temporal y falla al moverlo sobre el directorio no vacío que ocupa el destino PDF.
4. El servicio devuelve `pdfGenerated=false`. Se comprueba un solo pago persistido y factura en estado `Fallida`.
5. Se elimina el obstáculo y se reintenta con el mismo recibo y los mismos datos.
6. Se conservan `paymentId`, `invoiceId`, `numero_factura` y CUFE. La factura pasa a `Generada`, existe el PDF y continúan existiendo exactamente un pago y una factura asociados.
7. No quedan temporales huérfanos en el directorio de salida.

Los errores se registran en `error_log.txt`. La regeneración por ID también está disponible. El consecutivo ya confirmado se conserva; no se inserta otra factura para el reintento.

## 4. Préstamo concurrente entre ambas tablas

`ResourceService.lend` bloquea la fila de `recursos`, comprueba `Disponible`, inserta en la tabla correspondiente al enum `BorrowerType` y cambia el recurso a `Prestado` dentro de la misma transacción.

Como ambas clases de solicitante bloquean la misma fila, no dependen de que sus préstamos se almacenen en una sola tabla. Las pruebas comprobaron el conteo combinado de `prestamos_estudiante` y `prestamos_profesor` y el estado final del inventario.

Se mantienen las dos tablas y sus claves foráneas. La exclusividad entre tablas está garantizada por el servicio y el bloqueo compartido del recurso; no hay una restricción SQL declarativa cruzada que impida saltarse este protocolo escribiendo directamente en ambas tablas.

## 5. Estructura Maven y separación de capas

La estructura de producción verificada es:

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

`MavenStructureTest` recorre todos los archivos Java de producción, exige esa raíz y compara cada declaración de paquete con su directorio. También comprueba `Main.class`: cabecera válida y versión de bytecode **61**, correspondiente a Java 17. El `pom.xml` fija `maven.compiler.release=17`.

Las búsquedas estáticas no encontraron SQL/JDBC en vistas o controladores ni lecturas de consola en DAO. Las llamadas a `setAutoCommit`, `commit` y `rollback` de producción se limitan a `service/TransactionalService.java`.

La implementación de esta sesión conserva esa estructura. Git no contiene commits todavía, por lo que no existe un historial versionado para certificar revisiones anteriores de forma independiente.

## 6. Empaquetado y reproducción

Durante las ejecuciones repetidas se detectó que Maven podía volver a usar un JAR ya combinado por Shade, generando avisos de clases duplicadas. Se configuró `maven-jar-plugin` con `forceCreation=true` y el script ejecuta ahora `mvn clean verify` para reconstruir desde fuentes. Se repitió además `mvn -DskipTests package`: terminó correctamente sin los avisos de clases duplicadas observados antes; el JAR resultante abrió el menú y permitió salir.

```bash
./scripts/test-integration.sh
```

Distribución de pruebas: 19 independientes de MySQL y 22 de integración con MySQL. Informes: `target/surefire-reports/`. La prueba de semillas exige al menos 30 registros en cada una de las 26 tablas.

Estas son pruebas de concurrencia y fallos controlados; no constituyen una medición de rendimiento institucional ni una garantía universal de ausencia de defectos.

## 7. Checklist de Entrega

`[X]` indica evidencia verificada dentro de este alcance. Se conservan dos casillas pendientes porque no hay CRUD completo en todos los módulos y la rama `main` todavía no tiene commits. Los identificadores y comentarios Java están en inglés; los mensajes de interfaz y los valores de la BD permanecen en español, conforme a las reglas del proyecto.

✅ Código y Estructura

- [X] Proyecto Java estándar creado correctamente (Maven, Java 17)
- [X] Estructura MVC+DAO implementada correctamente
- [X] Código completamente en inglés
- [X] Convenciones de nomenclatura Java aplicadas

✅ Base de Datos

- [X] BD erp_little_horizons creada
- [X] Script database.sql incluye DDL completo (MySQL 8.0+, CHECK constraints)
- [X] Script incluye DML con mínimo 30 registros por tabla
- [X] Separación de tablas de préstamos respetada

✅ Funcionalidades

- [ ] CRUD y reglas de negocio funcionales en todos los módulos
- [X] Validación de entradas y aislamiento en las Vistas
- [X] Generación de facturas PDF con PDFBox y ZXing

✅ Calidad del Código

- [X] Lógica de transacciones aislada en capa Service
- [X] PreparedStatement y try-with-resources aplicados
- [X] Manejo de errores centralizado (java.util.logging en error_log.txt)
- [X] Lambdas y Stream API utilizados para colecciones

✅ Documentación y GitHub

- [X] Carpeta docs/ con database.sql e implementacion_log.md
- [X] README.md completo con 16 secciones
- [ ] Repositorio y commits listos (simulado)

✅ Pruebas Finales

- [X] Transacciones hacen rollback correcto ante fallos
- [X] Pruebas límite superadas
