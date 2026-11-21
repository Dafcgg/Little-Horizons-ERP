package com.littlehorizons.erp.view;

import com.littlehorizons.erp.model.AttendanceImportResult;
import com.littlehorizons.erp.model.AttendanceStatus;
import java.util.Locale;

public final class AttendanceImportView {
  private final ConsoleView console;

  public AttendanceImportView(ConsoleView console) {
    this.console = console;
  }

  public void showError(String message) {
    console.show("\n" + message);
  }

  public void showReport(AttendanceImportResult result) {
    String status = result.failed() > 0 ? "⚠ Carga con rechazos" : "✔ Carga completada";
    if (result.total() == 0) status = "Carga sin registros";
    console.show("\n" + String.format(Locale.ROOT, """
        ╔══════════════════════════════════════════════════════════════════╗
        ║           REPORTE DE IMPORTACIÓN DE ASISTENCIAS                  ║
        ╚══════════════════════════════════════════════════════════════════╝
          Archivo procesado : asistencias_importar.txt
          Estado general    : %s (%.2f%% de efectividad)

          RESUMEN DE PROCESAMIENTO
          ──────────────────────────────────────────────────────────────────
          Total de líneas en el archivo: %d
          Líneas procesadas exitosamente: %d
          Líneas con errores (no procesadas): %d

          DESGLOSE RÁPIDO POR ESTADO
          ──────────────────────────────────────────────────────────────────
          • Presente      : %-5d • Tardanza      : %d
          • Ausente       : %-5d • Excusa Médica : %d

          📄 Reporte detallado con nombres y auditoría guardado en:
             reports/%s
          💡 Consejo: Abre el archivo anterior si deseas ver el detalle por
             estudiante, materia o el motivo exacto de cualquier fila rechazada.
        ═══════════════════════════════════════════════════════════════════
        Presione Enter para volver al menú principal...
        ═══════════════════════════════════════════════════════════════════""",
        status, result.effectiveness(), result.total(), result.successful(), result.failed(),
        result.count(AttendanceStatus.PRESENT), result.count(AttendanceStatus.LATE),
        result.count(AttendanceStatus.ABSENT), result.count(AttendanceStatus.MEDICAL_EXCUSE),
        result.report().getFileName()));
    console.awaitEnter();
  }
}
