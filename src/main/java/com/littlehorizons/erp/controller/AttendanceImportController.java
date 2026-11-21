package com.littlehorizons.erp.controller;

import com.littlehorizons.erp.service.AttendanceImportService;
import com.littlehorizons.erp.util.AppLogger;
import com.littlehorizons.erp.util.BusinessException;
import com.littlehorizons.erp.util.ReportDownload;
import com.littlehorizons.erp.view.AttendanceImportView;
import java.io.IOException;
import java.nio.file.Path;

public final class AttendanceImportController {
  private final AttendanceImportService service;
  private final AttendanceImportView view;
  private final Path projectDirectory;
  private final ReportDownload reportDownload;

  public AttendanceImportController(
      AttendanceImportService service, AttendanceImportView view, Path projectDirectory,
      ReportDownload reportDownload) {
    this.service = service;
    this.view = view;
    this.projectDirectory = projectDirectory;
    this.reportDownload = reportDownload;
  }

  public void run() {
    try {
      var result = service.importFrom(projectDirectory);
      view.showReport(result, reportDownload.copy(result.report()));
    } catch (BusinessException rejection) {
      view.showError(rejection.getMessage());
    } catch (IOException error) {
      AppLogger.error("Attendance TXT import I/O failed", error);
      view.showError(
          "Error: No se pudo leer el TXT o escribir el reporte. Las filas ya confirmadas se conservan."
              + " Consulte error_log.txt.");
    }
  }
}
