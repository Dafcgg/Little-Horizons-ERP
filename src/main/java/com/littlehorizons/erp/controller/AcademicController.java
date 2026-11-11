package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.AcademicService;
import com.littlehorizons.erp.view.ConsoleView;

public final class AcademicController {
  private final AcademicService service;
  private final ConsoleView view;

  public AcademicController(AcademicService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Gestión académica y profesores",
        new Action("Listar profesores", () -> view.showAll(service.teachers())),
        new Action(
            "Registrar profesor",
            () ->
                view.show(
                    "Profesor: "
                        + service.registerTeacher(
                            new Teacher(
                                0,
                                view.required("Documento"),
                                view.required("Nombre"),
                                view.required("Apellido"),
                                view.required("Correo"),
                                view.required("Especialización"))))),
        new Action("Listar materias", () -> view.showAll(service.subjects())),
        new Action(
            "Registrar materia",
            () ->
                view.show(
                    "Materia: "
                        + service.registerSubject(
                            new Subject(
                                0,
                                view.required("Código"),
                                view.required("Nombre"),
                                view.id("ID grado"))))),
        new Action(
            "Asignar profesor y horario",
            () -> {
              service.assign(
                  view.id("ID profesor"),
                  view.id("ID materia"),
                  view.year(),
                  view.required("Grupo"),
                  view.integer("Día (1=lunes, 7=domingo)", 1, 7),
                  view.time("Inicio"),
                  view.time("Fin"));
              view.show("Horario asignado.");
            }),
        new Action(
            "Inscribir estudiante en materia",
            () -> {
              service.subscribe(
                  view.id("ID estudiante"),
                  view.id("ID materia"),
                  view.year(),
                  view.choice("Período", AcademicPeriod.class),
                  view.required("Grupo"));
              view.show("Inscripción registrada.");
            }),
        new Action(
            "Registrar / corregir nota",
            () -> {
              service.recordGrade(
                  new AcademicRecord(
                      view.id("ID estudiante"),
                      view.id("ID materia"),
                      view.id("ID profesor"),
                      view.year(),
                      view.choice("Período", AcademicPeriod.class),
                      view.decimal("Nota 0.0–5.0")));
              view.show("Nota guardada.");
            }),
        new Action(
            "Registrar / corregir asistencia",
            () -> {
              service.recordAttendance(
                  view.id("ID estudiante"),
                  view.id("ID materia"),
                  view.id("ID profesor"),
                  view.year(),
                  view.choice("Período", AcademicPeriod.class),
                  view.date("Fecha"),
                  view.choice("Estado", AttendanceStatus.class));
              view.show("Asistencia guardada.");
            }),
        new Action(
            "Historial académico", () -> view.showAll(service.history(view.id("ID estudiante")))),
        new Action(
            "Promedio por materia (parcial si faltan períodos)",
            () ->
                view.show(
                    service
                        .subjectAverage(
                            view.id("ID estudiante"), view.id("ID materia"), view.year())
                        .map(Object::toString)
                        .orElse("Sin notas"))),
        new Action(
            "Promedio del estudiante en un período",
            () ->
                view.show(
                    service
                        .periodAverage(
                            view.id("ID estudiante"),
                            view.year(),
                            view.choice("Período", AcademicPeriod.class))
                        .map(Object::toString)
                        .orElse("Sin notas"))),
        new Action(
            "Promedio del curso por materia y período",
            () ->
                view.show(
                    service
                        .courseAverage(
                            view.id("ID materia"),
                            view.year(),
                            view.choice("Período", AcademicPeriod.class),
                            view.required("Grupo"))
                        .map(Object::toString)
                        .orElse("Sin notas"))),
        new Action(
            "Reporte de inasistencias",
            () ->
                view.showAll(
                    service.absences(
                        view.date("Desde"),
                        view.date("Hasta"),
                        view.integer("Más de cuántas ausencias", 0, 1000)))));
  }
}
