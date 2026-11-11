package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.AcademicService;
import com.littlehorizons.erp.view.ConsoleView;
import java.util.List;

public final class AcademicAdministrationController {
  private final AcademicService service;
  private final ConsoleView view;

  public AcademicAdministrationController(AcademicService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Edición académica",
        new Action("Consultar profesor", () -> view.show(service.teacher(view.id("ID profesor")))),
        new Action("Editar profesor / estado", this::editTeacher),
        new Action(
            "Inactivar profesor",
            () -> {
              service.deactivateTeacher(view.id("ID profesor"));
              view.show("Profesor inactivado.");
            }),
        new Action("Consultar materia", () -> view.show(service.subject(view.id("ID materia")))),
        new Action("Editar materia / reactivar", this::editSubject),
        new Action(
            "Inactivar materia",
            () -> {
              service.deactivateSubject(view.id("ID materia"));
              view.show("Materia inactivada.");
            }),
        new Action("Listar asignaciones", () -> view.showAll(service.assignments())),
        new Action("Editar asignación / reactivar", this::editAssignment),
        new Action(
            "Inactivar asignación",
            () -> {
              service.deactivateAssignment(view.id("ID asignación"));
              view.show("Asignación inactivada.");
            }),
        new Action("Listar horarios", () -> view.showAll(service.schedules())),
        new Action(
            "Editar horario",
            () -> {
              service.updateSchedule(
                  view.id("ID horario"),
                  view.integer("Día (1=lunes, 7=domingo)", 1, 7),
                  view.time("Inicio"),
                  view.time("Fin"));
              view.show("Horario actualizado.");
            }),
        new Action(
            "Eliminar horario",
            () -> {
              service.deleteSchedule(view.id("ID horario"));
              view.show("Horario eliminado.");
            }),
        new Action(
            "Consultar inscripciones",
            () -> view.showAll(service.subscriptions(view.id("ID estudiante")))),
        new Action(
            "Editar grupo / estado de inscripción",
            () -> {
              service.updateSubscription(
                  view.id("ID inscripción"),
                  view.required("Grupo"),
                  view.option("Estado", List.of("Activa", "Retirada", "Completada")));
              view.show("Inscripción actualizada.");
            }),
        new Action(
            "Retirar inscripción",
            () -> {
              service.withdrawSubscription(view.id("ID inscripción"));
              view.show("Inscripción retirada.");
            }),
        new Action(
            "Eliminar nota",
            () -> {
              service.deleteGrade(
                  view.id("ID estudiante"),
                  view.id("ID materia"),
                  view.year(),
                  view.choice("Período", AcademicPeriod.class));
              view.show("Nota eliminada; cierre final invalidado.");
            }),
        new Action(
            "Consultar asistencias",
            () -> view.showAll(service.attendances(view.id("ID estudiante")))),
        new Action(
            "Eliminar asistencia",
            () -> {
              service.deleteAttendance(view.id("ID asistencia"));
              view.show("Asistencia eliminada.");
            }),
        new Action(
            "Calcular / guardar nota final",
            () ->
                view.show(
                    "Nota final: "
                        + service.closeSubject(
                            view.id("ID estudiante"), view.id("ID materia"), view.year()))));
  }

  private void editTeacher() {
    Teacher t = service.teacher(view.id("ID profesor"));
    view.show(t);
    service.updateTeacher(
        new Teacher(
            t.id(),
            view.edit("Documento", t.document()),
            view.edit("Nombre", t.firstName()),
            view.edit("Apellido", t.lastName()),
            view.edit("Correo", t.email()),
            view.edit("Especialización", t.specialization()),
            view.option("Estado", List.of("Activo", "Inactivo", "Licencia"))));
    view.show("Profesor actualizado.");
  }

  private void editSubject() {
    Subject s = service.subject(view.id("ID materia"));
    view.show(s);
    service.updateSubject(
        new Subject(
            s.id(),
            view.edit("Código", s.code()),
            view.edit("Nombre", s.name()),
            view.editId("ID grado", s.gradeId()),
            view.editFlag("¿Activa?", s.active())));
    view.show("Materia actualizada.");
  }

  private void editAssignment() {
    long id = view.id("ID asignación");
    TeachingAssignment a =
        service.assignments().stream()
            .filter(e -> e.id() == id)
            .findFirst()
            .orElseThrow(
                () -> new com.littlehorizons.erp.util.BusinessException("Asignación inexistente."));
    view.show(a);
    service.updateAssignment(
        new TeachingAssignment(
            id,
            view.editId("ID profesor", a.teacherId()),
            view.editId("ID materia", a.subjectId()),
            view.editInteger("Año", a.year(), 2000, 2100),
            view.edit("Grupo", a.section()),
            view.editFlag("¿Activa?", a.active())));
    view.show("Asignación actualizada.");
  }
}
