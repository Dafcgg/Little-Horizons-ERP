package com.littlehorizons.erp.view;

import com.littlehorizons.erp.model.AttendanceImportResult;

public final class AttendanceImportView {
  private final ConsoleView console;

  public AttendanceImportView(ConsoleView console) {
    this.console = console;
  }

  public void showError(String message) {
    console.show("\n" + message);
  }

  public void showReport(AttendanceImportResult result) {
    console.show("\n" + """
        ╔══════════════════════════════════════════════════════════════════╗
        ║           REPORTE DE IMPORTACIÓN DE ASISTENCIAS                  ║
        ╚══════════════════════════════════════════════════════════════════╝

        Total de líneas en el archivo: %d
        Líneas procesadas exitosamente: %d
        Líneas con errores (no procesadas): %d
        Reporte detallado generado en: reports/%s

        ═══════════════════════════════════════════════════════════════════
        Presione Enter para volver al menú principal...
        ═══════════════════════════════════════════════════════════════════"""
        .formatted(result.total(), result.successful(), result.failed(), result.report().getFileName()));
    console.awaitEnter();
  }
}
