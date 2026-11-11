package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.CatalogService;
import com.littlehorizons.erp.view.ConsoleView;

public final class CatalogController {
  private final CatalogService service;
  private final ConsoleView view;

  public CatalogController(CatalogService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    CatalogType type = view.choice("Catálogo", CatalogType.class);
    view.menu(
        "Administración de catálogos",
        new Action("Listar, incluidos inactivos", () -> view.showAll(service.list(type))),
        new Action("Consultar registro", () -> view.show(service.get(type, view.id("ID")))),
        new Action(
            "Crear registro",
            () ->
                view.show(
                    "Registro: "
                        + service.create(
                            type,
                            new CatalogEntry(
                                0,
                                view.required("Nombre"),
                                type == CatalogType.GRADE
                                    ? view.integer("Nivel (0=preescolar)", 0, 11)
                                    : 0,
                                true)))),
        new Action(
            "Editar / reactivar",
            () -> {
              CatalogEntry e = service.get(type, view.id("ID"));
              view.show(e);
              service.update(
                  type,
                  new CatalogEntry(
                      e.id(),
                      view.edit("Nombre", e.name()),
                      type == CatalogType.GRADE ? view.editInteger("Nivel", e.level(), 0, 11) : 0,
                      view.editFlag("¿Activo?", e.active())));
              view.show("Catálogo actualizado.");
            }),
        new Action(
            "Inactivar",
            () -> {
              service.deactivate(type, view.id("ID"));
              view.show("Registro inactivado; historial conservado.");
            }));
  }
}
