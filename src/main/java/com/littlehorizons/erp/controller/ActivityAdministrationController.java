package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.ActivityService;
import com.littlehorizons.erp.view.ConsoleView;
import java.util.List;

public final class ActivityAdministrationController {
  private final ActivityService service;
  private final ConsoleView view;

  public ActivityAdministrationController(ActivityService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Edición extracurricular",
        new Action("Consultar club", () -> view.show(service.club(view.id("ID club")))),
        new Action("Editar club / reactivar", this::editClub),
        new Action(
            "Inactivar club y retirar miembros",
            () -> {
              service.deactivateClub(view.id("ID club"));
              view.show("Club inactivado; historial conservado.");
            }),
        new Action(
            "Consultar membresías", () -> view.showAll(service.memberships(view.id("ID club")))),
        new Action(
            "Editar fecha / estado de membresía",
            () -> {
              service.updateMembership(
                  view.id("ID membresía"),
                  view.date("Fecha ingreso"),
                  view.option("Estado", List.of("Activa", "Retirada")));
              view.show("Membresía actualizada.");
            }),
        new Action("Consultar evento", () -> view.show(service.event(view.id("ID evento")))),
        new Action("Editar evento / reactivar", this::editEvent),
        new Action(
            "Inactivar evento",
            () -> {
              service.deactivateEvent(view.id("ID evento"));
              view.show("Evento inactivado.");
            }),
        new Action(
            "Consultar participantes",
            () -> view.showAll(service.participants(view.id("ID evento")))),
        new Action(
            "Editar resultado",
            () -> {
              service.updateParticipation(
                  new EventParticipation(
                      view.id("ID evento"),
                      view.id("ID estudiante"),
                      view.read("Posición (vacío para borrar)")));
              view.show("Participación actualizada.");
            }),
        new Action(
            "Eliminar participante",
            () -> {
              service.removeParticipant(view.id("ID evento"), view.id("ID estudiante"));
              view.show("Participante eliminado.");
            }));
  }

  private void editClub() {
    Club c = service.club(view.id("ID club"));
    view.show(c);
    service.updateClub(
        new Club(
            c.id(),
            view.edit("Nombre", c.name()),
            view.editId("ID profesor", c.teacherId()),
            view.editInteger("Cupo", c.capacity(), 1, 10000),
            view.editFlag("¿Activo?", c.active())));
    view.show("Club actualizado.");
  }

  private void editEvent() {
    SchoolEvent e = service.event(view.id("ID evento"));
    view.show(e);
    service.updateEvent(
        new SchoolEvent(
            e.id(),
            view.edit("Nombre", e.name()),
            view.editDate("Fecha", e.date()),
            view.option(
                "Tipo", List.of("Académico", "Cultural", "Deportivo", "Social", "Científico")),
            view.editFlag("¿Competencia?", e.competitive()),
            view.editFlag("¿Activo?", e.active())));
    view.show("Evento actualizado.");
  }
}
