package com.littlehorizons.erp;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.controller.*;
import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.*;
import com.littlehorizons.erp.view.ConsoleView;
import java.io.IOException;
import java.nio.file.Path;

public final class Main {
  private Main() {}

  public static void main(String[] args) {
    try {
      AppLogger.configure();
    } catch (IOException error) {
      System.err.println("No se pudo abrir error_log.txt: " + error.getMessage());
      return;
    }
    var connections = new DatabaseConnection();
    var view = new ConsoleView(System.in, System.out);
    var studentService = new StudentService(connections, new JdbcStudentDao());
    var academicService = new AcademicService(connections, new JdbcAcademicDao());
    var activityService = new ActivityService(connections, new JdbcActivityDao());
    var financeService =
        new FinanceService(
            connections,
            new JdbcFinanceDao(),
            new PdfInvoiceGenerator(
                Path.of(System.getenv().getOrDefault("ERP_INVOICE_DIR", "invoices"))),
            SchoolInformation.fromEnvironment());
    var resourceService = new ResourceService(connections, new JdbcResourceDao());
    var students = new StudentController(studentService, view);
    var academics = new AcademicController(academicService, view);
    var activities = new ActivityController(activityService, view);
    var finance = new FinanceController(financeService, view);
    var resources = new ResourceController(resourceService, view);
    Runnable administration =
        () ->
            view.menu(
                "Administración, edición e inactivación",
                new Action(
                    "Estudiantes, acudientes y matrículas",
                    new StudentAdministrationController(studentService, view)::run),
                new Action(
                    "Académico, profesores y horarios",
                    new AcademicAdministrationController(academicService, view)::run),
                new Action(
                    "Clubes y eventos",
                    new ActivityAdministrationController(activityService, view)::run),
                new Action(
                    "Finanzas y anulaciones",
                    new FinanceAdministrationController(financeService, view)::run),
                new Action(
                    "Recursos y préstamos",
                    new ResourceAdministrationController(resourceService, view)::run),
                new Action(
                    "Grados y categorías",
                    new CatalogController(
                            new CatalogService(connections, new JdbcCatalogDao()), view)
                        ::run));
    try {
      view.menu(
          "ERP Colegio Little Horizons",
          new Action("Estudiantes, acudientes y matrícula", students::run),
          new Action("Académico, profesores y asistencia", academics::run),
          new Action("Clubes y eventos", activities::run),
          new Action("Finanzas y facturación", finance::run),
          new Action("Inventario y préstamos", resources::run),
          new Action("Administración, edición e inactivación", administration));
    } catch (ConsoleView.EndOfInput ignored) {
      view.show("Fin de entrada.");
    }
    view.show("Hasta pronto.");
  }
}
