# Verificación del código base

La entrega se verifica con Java 17, Maven y MySQL 8. Las pruebas unitarias comprueban precisión y límites de notas/importes, promedios, CUFE determinista y navegación de consola. Las pruebas de integración usan el esquema completo de `docs/database.sql`.

## Ejecución reproducible

```bash
./scripts/test-integration.sh
```

La prueba arranca su propio MySQL 8, importa el esquema y ejecuta `mvn verify`. La configuración de pruebas es independiente de `ERP_DB_URL` y no necesita el servidor de trabajo. El script elimina el contenedor y sus volúmenes al terminar.

## Escenarios automáticos

- Las 26 tablas contienen al menos 30 filas.
- Un vínculo con acudiente inexistente revierte el estudiante insertado.
- MySQL rechaza notas fuera de rango incluso al omitir el servicio.
- Dos inscripciones concurrentes compiten por el último cupo y solo una se confirma.
- Retiro y reingreso conservan el historial del club.
- Un recurso no puede prestarse al mismo tiempo a un estudiante y un profesor.
- Un solicitante inexistente no deja el recurso marcado como prestado.
- El fallo deliberado del generador conserva el pago, registra la factura fallida y permite reintentar con el mismo recibo sin duplicar registros.
- El PDF regenerado contiene importes correctos y advertencia de simulación; ZXing decodifica el QR incrustado y comprueba su contenido.
- Un fallo SQL al guardar la factura revierte también el pago de esa transacción.
- Los abonos parciales liquidan la deuda exactamente y las facturas conservan los datos originales cuando cambia el acudiente.
- Una deuda sin pagos aparece en el reporte de mora.
- Dos pagos concurrentes no pueden exceder el saldo de una obligación.
- Matrícula, horario, inscripción, corrección de notas y asistencia funcionan juntos; se detectan cruces y se rechaza a un profesor no asignado.
- Los eventos sin competencia rechazan posiciones; no se duplican participantes.

Los informes de JUnit quedan en `target/surefire-reports/`. Sin `ERP_TEST_DB_URL`, las pruebas MySQL se omiten explícitamente. Las pruebas con errores deliberados generan entradas en `error_log.txt`.

## Recorrido de consola

1. Configurar `ERP_DB_*` e instalar el SQL en un servidor distinto al contenedor efímero de pruebas.
2. Ejecutar el JAR, listar estudiantes/profesores y consultar historial.
3. Registrar estudiante con acudiente, matricularlo e inscribirlo en una materia compatible.
4. Registrar nota y asistencia desde el profesor asignado.
5. Inscribirlo en un club y consultar los eventos.
6. Crear una obligación y pagar con recibo único; abrir el PDF indicado.
7. Consultar inventario y devolver un préstamo de profesor de la semilla; prestar ese recurso nuevamente a un estudiante.
8. Consultar mora y préstamos vencidos en sus respectivos módulos.
9. Salir con `0` y comprobar el registro de errores controlados.

Las limitaciones de esta base están enumeradas en README. La publicación privada, colaboradores, identidad de autores y licencia requieren los datos reales del equipo y no forman parte de estas pruebas locales.

## Auditoría posterior

La auditoría del 10 de noviembre de 2026 amplió la suite a 36 pruebas: 18 independientes de MySQL y 18 de integración. Todas pasaron sin fallos, errores ni omisiones en el contenedor aislado. Se añadieron verificaciones de cierre de recursos, tarifas negativas, beca total, sobrepago, desbordamiento del total con IVA, matrículas retiradas, cheque, fecha/CUFE, nombre de archivo, codificación de fuente, paginación y limpieza de temporales. Las decisiones de negocio de horarios, inscripción, eventos e importe de obligaciones quedan en servicios.

El detalle de hallazgos y certificación funcional está en [implementacion_log.md](implementacion_log.md).

## Simulación de estrés posterior

La suite ampliada contiene 41 pruebas (19 independientes de MySQL y 22 con MySQL). `StressScenariosTest` ejecuta 100 intentos de inscripción, 100 intentos de préstamo, validación de rango/precisión y fallo real de escritura con recuperación de pago y consecutivo. `MavenStructureTest` comprueba la raíz Maven, paquetes y bytecode Java 17. Véase [qa_stress_report.md](qa_stress_report.md) para resultados y checklist con pendientes explícitos.

## Cierre CRUD

`CompleteCrudIntegrationTest` incorpora 20 escenarios adicionales; resultado actual: 61 pruebas aprobadas, sin fallos, errores ni omisiones. Las pruebas cubren los cuatro módulos y las pantallas administrativas, la corrección/anulación financiera, las restricciones de historial y la concurrencia de nuevas operaciones de edición. Véase [crud_matrix.md](crud_matrix.md).

## Revalidación A01–A12

La auditoría final corrigió una regresión A02 en `StudentService.updateStudent` (estado nulo) y añadió una prueba que exige rechazo antes de conectar. También reforzó la comprobación de contacto inicial principal, de emergencia y financiero. Resultado actualizado: 62 pruebas aprobadas, 20 independientes de MySQL y 42 de integración. La evidencia vigente se consolida en [implementacion_log.md](implementacion_log.md).
