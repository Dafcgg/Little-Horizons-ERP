package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.ActivityService;
import com.littlehorizons.erp.view.ConsoleView;

public final class ActivityController {
  private final ActivityService service;
  private final ConsoleView view;

  public ActivityController(ActivityService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Actividades extracurriculares",
        new Action("Listar clubes", () -> view.showAll(service.clubs())),
        new Action(
            "Crear club",
            () ->
                view.show(
                    "Club: "
                        + service.createClub(
                            new Club(
                                0,
                                view.required("Nombre"),
                                view.id("ID profesor responsable"),
                                view.integer("Cupo", 1, 10000))))),
        new Action(
            "Ingresar a club",
            () -> {
              service.join(view.id("ID club"), view.id("ID estudiante"));
              view.show("Ingreso registrado.");
            }),
        new Action(
            "Retirar del club",
            () -> {
              service.withdraw(view.id("ID club"), view.id("ID estudiante"));
              view.show("Retiro registrado.");
            }),
        new Action("Listar eventos", () -> view.showAll(service.events())),
        new Action(
            "Crear evento",
            () ->
                view.show(
                    "Evento: "
                        + service.createEvent(
                            new SchoolEvent(
                                0,
                                view.required("Nombre"),
                                view.date("Fecha"),
                                view.required(
                                    "Tipo: Académico, Cultural, Deportivo, Social o Científico"),
                                view.integer("¿Competencia? 1=sí, 0=no", 0, 1) == 1)))),
        new Action(
            "Registrar participante",
            () -> {
              service.participate(
                  view.id("ID evento"),
                  view.id("ID estudiante"),
                  view.read(
                      "Posición (vacío sin competencia; 1er Lugar, 2do Lugar, 3er Lugar,"
                          + " Participante)"));
              view.show("Participante registrado.");
            }));
  }
}
