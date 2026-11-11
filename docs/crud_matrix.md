# CRUD de los cuatro módulos

Estado: implementado y validado en esta fase. La supresión se representa como inactivación, retiro, baja o anulación cuando existe historial. Las claves de las relaciones y los documentos financieros emitidos no se sobrescriben para trasladar su identidad.

## Acceso desde consola

El menú principal conserva los cinco grupos de operación y añade **6. Administración, edición e inactivación**. Este último abre seis pantallas: estudiantes/acudientes, académico, extracurricular, finanzas, recursos y catálogos. Las altas siguen disponibles en los menús originales; grados y categorías se crean en administración.

Los formularios de edición muestran el valor actual entre corchetes. Enter lo conserva; `q` cancela la operación completa antes de llamar al servicio. Las elecciones y mensajes son en español. Las consultas muestran etiquetas del dominio en español en lugar de representaciones internas de records Java.

## Matriz por entidad

| Tabla / entidad | Crear | Consultar | Actualizar | Suprimir / inactivar |
| --- | --- | --- | --- | --- |
| `grados` | `CatalogService.create` | `list/get` | `update` nombre/nivel/estado | `deactivate`; conserva referencias e impide cambiar niveles con historial. |
| `estudiantes` | `StudentService.register/registerWithGuardian` | `students/student` | `updateStudent` | `deactivateStudent`: estado Retirado. |
| `acudientes` | `registerGuardian` | `guardians/guardian` | `updateGuardian` | `deactivateGuardian`; exige otro acudiente activo para cada estudiante relacionado. |
| `estudiante_acudiente` | `saveLink` | `links` | `saveLink` parentesco y responsabilidades | `removeLink`; nunca elimina el último acudiente activo y mantiene un contacto principal activo. |
| `matriculas` | `enroll` | `enrollments` | `updateEnrollment` grado/estado | `withdrawEnrollment`; no cambia el grado si existen inscripciones. |
| `profesores` | `AcademicService.registerTeacher` | `teachers/teacher` | `updateTeacher` | `deactivateTeacher`; conserva historial. |
| `materias` | `registerSubject` | `subjects/subject` | `updateSubject` | `deactivateSubject`; no mueve materias con historial a otro grado. |
| `asignaciones_profesor` | `assign` | `assignments` | `updateAssignment` | `deactivateAssignment`; conserva sus horarios e historial. |
| `horarios` | `assign` con franja | `schedules` | `updateSchedule` | `deleteSchedule`. |
| `inscripciones_materia` | `subscribe` | `subscriptions` | `updateSubscription` grupo/estado | `withdrawSubscription`; el grupo queda fijo después de notas/asistencia. |
| `registros_academicos` | `recordGrade`; Final derivada mediante `closeSubject` | `history`, promedios | `recordGrade` corrige I–IV; `closeSubject` recalcula Final | `deleteGrade` elimina nota y cierre derivado. |
| `asistencias` | `recordAttendance` | `attendances`, `absences` | `recordAttendance` corrige la clave estudiante/materia/fecha | `deleteAttendance`. |
| `clubes` | `ActivityService.createClub` | `clubs/club` | `updateClub` | `deactivateClub`, retirando membresías activas atómicamente. |
| `membresias_club` | `join` | `memberships` | `updateMembership` fecha/estado; reactivación protegida por cupo | `withdraw` o estado Retirada. |
| `eventos` | `createEvent` | `events/event` | `updateEvent` | `deactivateEvent`; conserva participantes. |
| `participantes_evento` | `participate` | `participants` | `updateParticipation` posición | `removeParticipant`. |
| `conceptos_pago` | `FinanceService.createConcept` | `concepts/conceptEntries/concept` | `updateConcept` | `deactivateConcept`; solo bloquea nuevos cobros. |
| `tarifas_estudiante` | `setFee` | `fees` | `setFee` | `removeFee`; próximos cobros vuelven al precio base. |
| `obligaciones` | `createObligation` | `obligations/obligation` | `updateObligation` referencia/vencimiento/importe | `cancelObligation`; exige cero pagos confirmados. |
| `pagos` | `confirmPayment` | `payments/payment/history` | `correctPayment` anula y crea reemplazo atómico | `voidPayment`; conserva recibo y trazabilidad. |
| `facturas` | Automática al confirmar pago | `invoices/invoice` | `correctInvoice` reemplaza con nuevo documento; `regenerate` reconstruye PDF | `voidInvoice`, anulando también su pago. |
| `detalles_factura` | Automático con factura | `invoice.lines` | Detalle corregido en factura de reemplazo | Se conserva como parte de la factura anulada. |
| `categorias_recurso` | `CatalogService.create` | `list/get` | `update` | `deactivate`; impide usarla para nuevos recursos. |
| `recursos` | `ResourceService.create` | `resources/resource` | `updateResource`, `changeStatus` | `deactivateResource`; rechaza baja mientras está prestado. |
| `prestamos_estudiante` | `lend` tipo STUDENT | `loans/loan/overdue` | `updateDueDate` | `returnLoan` o `cancelLoan`; libera el recurso y conserva historial. |
| `prestamos_profesor` | `lend` tipo TEACHER | `loans/loan/overdue` | `updateDueDate` | `returnLoan` o `cancelLoan`; libera el recurso y conserva historial. |

## Reglas e invariantes

- Identificadores Java, clases y comentarios permanecen en inglés. Tablas, campos SQL e interfaz permanecen en español.
- No se añadieron paquetes raíz `config/` o `exception/`. Conexión y excepciones continúan en `util/`.
- Todas las transacciones pertenecen a servicios y emplean `READ_COMMITTED`. Esta elección permite que los conteos vean cambios confirmados después de adquirir el bloqueo, incluso si hubo una consulta previa en la operación.
- Clubes y recursos mantienen `FOR UPDATE` sobre la fila compartida antes de validar capacidad/disponibilidad. Editar cupos y restaurar membresías utiliza el mismo bloqueo que las altas.
- Las notas se validan en Java: `5.5` y `3.45` se rechazan. El cierre Final requiere cuatro períodos, usa pesos iguales y se invalida al modificar/eliminar una nota. Cambios de notas y cierres se serializan mediante la fila del estudiante.
- La edición de horarios y reactivación de asignaciones revalida también las franjas previamente guardadas. No basta con validar la nueva franja añadida.
- No se permite disminuir una obligación por debajo de los abonos confirmados ni anularla mientras conserva pagos confirmados.
- Anular un pago excluye su importe del saldo y marca la factura Anulada; su PDF se reconstruye con el aviso visible y motivo de anulación. Si falla, el estado de PDF queda Fallida y se puede regenerar.
- Corregir un pago anula el original e inserta el reemplazo dentro de una sola transacción. Si el nuevo importe es inválido, se revierte también la anulación. El recibo nuevo y `id_pago_original` impiden duplicar el reemplazo en reintentos. La obligación original no cambia; para trasladar entre obligaciones se usa anulación y alta explícita.
- Los PDF se escriben después del commit. Bloqueos por factura dentro de la JVM serializan sus generadores; antes de marcar Generada se verifica que el estado de negocio no haya cambiado. La ejecución de varias JVM con un mismo directorio requeriría coordinación de archivos entre procesos.
- El historial de pagos por acudiente conserva también sus pagos propios aunque posteriormente se elimine su vínculo con el estudiante.

## Actualización de instalaciones existentes

En una instalación nueva, usar `docs/database.sql`: ya incorpora las columnas nuevas y conserva DDL/DML y el mínimo de 30 filas por tabla.

Para la base de la fase anterior, aplicar **una sola vez**:

```bash
mysql -u root -p --default-character-set=utf8mb4 < docs/migrations/002_complete_crud.sql
```

No aplicar la migración otra vez ni encima de una instalación nueva del script completo. Añade indicadores de actividad, estados de anulación, motivo y referencia al pago corregido, y amplía los estados de préstamos. No borra tablas ni datos. Se verificó sobre un esquema anterior reconstruido: conservó sus 30 estudiantes, 30 pagos y 30 facturas, y habilitó los nuevos flujos.

## Verificación

`CompleteCrudIntegrationTest` añade 20 pruebas a las 41 anteriores: **61 pruebas aprobadas**, sin fallos, errores ni omisiones en MySQL aislado. Incluye CRUD por entidad, pantallas administrativas conectadas a servicios reales, rollback de corrección financiera, anulación/PDF fallido y regeneración, integridad de acudientes, cierre académico, reactivación de horarios y concurrencia al restaurar membresías o reducir cupos.

```bash
./scripts/test-integration.sh
```

Las 19 pruebas independientes de MySQL y 42 de integración dejan informes en `target/surefire-reports/`. La entrega de GitHub, autores y licencia permanece separada de este alcance de implementación.
