package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.ResourceService;
import com.littlehorizons.erp.view.ConsoleView;

public final class ResourceController {
  private final ResourceService service;
  private final ConsoleView view;

  public ResourceController(ResourceService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Recursos y préstamos",
        new Action("Inventario", () -> view.showAll(service.resources())),
        new Action(
            "Registrar recurso",
            () ->
                view.show(
                    "Recurso: "
                        + service.create(
                            new SchoolResource(
                                0,
                                view.required("Código"),
                                view.required("Nombre"),
                                view.id("ID categoría"),
                                view.required("Ubicación"),
                                "Disponible")))),
        new Action(
            "Registrar préstamo",
            () ->
                view.show(
                    "Préstamo: "
                        + service.lend(
                            view.id("ID recurso"),
                            view.id("ID solicitante"),
                            view.choice("Tipo de solicitante", BorrowerType.class),
                            view.date("Devolución esperada")))),
        new Action(
            "Registrar devolución",
            () -> {
              service.returnLoan(
                  view.id("ID préstamo"), view.choice("Tipo de solicitante", BorrowerType.class));
              view.show("Devolución registrada.");
            }),
        new Action(
            "Cambiar estado",
            () -> {
              service.changeStatus(
                  view.id("ID recurso"),
                  view.required("Estado: Disponible, En Mantenimiento, Dado de Baja"));
              view.show("Estado actualizado.");
            }),
        new Action("Préstamos vencidos", () -> view.showAll(service.overdue())));
  }
}
