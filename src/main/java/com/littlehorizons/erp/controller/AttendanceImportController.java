package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.service.AttendanceImportService;
import com.littlehorizons.erp.util.AppLogger;
import com.littlehorizons.erp.util.BusinessException;
import com.littlehorizons.erp.view.AttendanceImportView;
import java.io.IOException;
import java.nio.file.Path;

public final class AttendanceImportController {
  private final AttendanceImportService service;
  private final AttendanceImportView view;
  private final Path projectDirectory;

  public AttendanceImportController(
      AttendanceImportService service, AttendanceImportView view, Path projectDirectory) {
    this.service = service;
    this.view = view;
    this.projectDirectory = projectDirectory;
  }

  public void run() {
    try {
      view.showReport(service.importFrom(projectDirectory));
    } catch (BusinessException rejection) {
      view.showError(rejection.getMessage());
    } catch (IOException error) {
      AppLogger.error("Attendance CSV import I/O failed", error);
      view.showError(
          "Error: No se pudo leer el CSV o escribir el reporte. Las filas ya confirmadas se conservan."
              + " Consulte error_log.txt.");
    }
  }
}
