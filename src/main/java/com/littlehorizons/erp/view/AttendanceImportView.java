package com.littlehorizons.erp.view;

import com.littlehorizons.erp.model.AttendanceImportResult;
import com.littlehorizons.erp.model.AttendanceStatus;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class AttendanceImportView {
  private final ConsoleView console;

  public AttendanceImportView(ConsoleView console) {
    this.console = console;
  }

  public void showError(String message) {
    console.show("\n" + message);
  }

  public void showReport(AttendanceImportResult result, Optional<Path> downloadedReport) {
    String status = result.failed() > 0 ? "⚠ Carga con rechazos" : "✔ Carga completada";
    if (result.total() == 0) status = "Carga sin registros";
    String downloadLine = downloadedReport
        .map(path -> "📥 Reporte descargado en el equipo  : " + path)
        .orElse("⚠ No se pudo guardar la copia en Descargas. Consulte error_log.txt.");
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

          📄 Reporte guardado en el proyecto : reports/%s
          %s
        ═══════════════════════════════════════════════════════════════════
        Presione Enter para volver al menú principal...
        ═══════════════════════════════════════════════════════════════════""",
        status, result.effectiveness(), result.total(), result.successful(), result.failed(),
        result.count(AttendanceStatus.PRESENT), result.count(AttendanceStatus.LATE),
        result.count(AttendanceStatus.ABSENT), result.count(AttendanceStatus.MEDICAL_EXCUSE),
        result.report().getFileName(), downloadLine));
    console.awaitEnter();
  }
}
