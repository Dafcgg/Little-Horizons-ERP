package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.ResourceService;
import com.littlehorizons.erp.view.ConsoleView;

public final class ResourceAdministrationController {
  private final ResourceService service;
  private final ConsoleView view;

  public ResourceAdministrationController(ResourceService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Edición de inventario y préstamos",
        new Action("Consultar recurso", () -> view.show(service.resource(view.id("ID recurso")))),
        new Action("Editar recurso", this::editResource),
        new Action(
            "Dar recurso de baja",
            () -> {
              service.deactivateResource(view.id("ID recurso"));
              view.show("Recurso dado de baja.");
            }),
        new Action(
            "Listar préstamos, incluidos cerrados",
            () -> view.showAll(service.loans(view.choice("Tipo", BorrowerType.class)))),
        new Action(
            "Consultar préstamo",
            () ->
                view.show(
                    service.loan(view.id("ID préstamo"), view.choice("Tipo", BorrowerType.class)))),
        new Action(
            "Editar vencimiento",
            () -> {
              service.updateDueDate(
                  view.id("ID préstamo"),
                  view.choice("Tipo", BorrowerType.class),
                  view.date("Nuevo vencimiento"));
              view.show("Vencimiento actualizado.");
            }),
        new Action(
            "Anular préstamo y liberar recurso",
            () -> {
              service.cancelLoan(
                  view.id("ID préstamo"),
                  view.choice("Tipo", BorrowerType.class),
                  view.required("Motivo"));
              view.show("Préstamo anulado; recurso disponible.");
            }));
  }

  private void editResource() {
    SchoolResource r = service.resource(view.id("ID recurso"));
    view.show(r);
    service.updateResource(
        new SchoolResource(
            r.id(),
            view.edit("Código", r.code()),
            view.edit("Nombre", r.name()),
            view.editId("ID categoría", r.categoryId()),
            view.edit("Ubicación", r.location()),
            r.status()));
    view.show("Recurso actualizado.");
  }
}
