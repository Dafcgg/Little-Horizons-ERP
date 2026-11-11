package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.StudentService;
import com.littlehorizons.erp.view.ConsoleView;
import java.util.List;

public final class StudentAdministrationController {
  private final StudentService service;
  private final ConsoleView view;

  public StudentAdministrationController(StudentService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Edición de estudiantes, acudientes y matrícula",
        new Action(
            "Consultar estudiante", () -> view.show(service.student(view.id("ID estudiante")))),
        new Action("Editar estudiante / estado", this::editStudent),
        new Action(
            "Retirar estudiante",
            () -> {
              service.deactivateStudent(view.id("ID estudiante"));
              view.show("Estudiante retirado; historial conservado.");
            }),
        new Action(
            "Consultar acudiente", () -> view.show(service.guardian(view.id("ID acudiente")))),
        new Action("Editar acudiente / reactivar", this::editGuardian),
        new Action(
            "Inactivar acudiente",
            () -> {
              service.deactivateGuardian(view.id("ID acudiente"));
              view.show("Acudiente inactivado.");
            }),
        new Action(
            "Consultar vínculos de acudientes",
            () -> view.showAll(service.links(view.id("ID estudiante")))),
        new Action(
            "Agregar / editar vínculo",
            () -> {
              service.saveLink(
                  new GuardianLink(
                      view.id("ID estudiante"),
                      view.id("ID acudiente"),
                      view.required("Parentesco"),
                      view.flag("¿Principal?"),
                      view.flag("¿Contacto de emergencia?"),
                      view.flag("¿Responsable financiero?")));
              view.show("Vínculo guardado.");
            }),
        new Action(
            "Eliminar vínculo",
            () -> {
              service.removeLink(view.id("ID estudiante"), view.id("ID acudiente"));
              view.show("Vínculo eliminado.");
            }),
        new Action(
            "Consultar matrículas",
            () -> view.showAll(service.enrollments(view.id("ID estudiante")))),
        new Action(
            "Editar grado / estado de matrícula",
            () -> {
              service.updateEnrollment(
                  view.id("ID matrícula"),
                  view.id("ID grado"),
                  view.option("Estado", List.of("Activa", "Completada", "Retirada")));
              view.show("Matrícula actualizada.");
            }),
        new Action(
            "Retirar matrícula",
            () -> {
              service.withdrawEnrollment(view.id("ID matrícula"));
              view.show("Matrícula retirada.");
            }));
  }

  private void editStudent() {
    Student s = service.student(view.id("ID estudiante"));
    view.show(s);
    service.updateStudent(
        new Student(
            s.id(),
            view.edit("Documento", s.document()),
            view.edit("Nombre", s.firstName()),
            view.edit("Apellido", s.lastName()),
            view.editDate("Nacimiento", s.birthDate()),
            view.edit("Dirección", s.address()),
            view.option("Estado", List.of("Activo", "Retirado", "Graduado", "Suspendido"))));
    view.show("Estudiante actualizado.");
  }

  private void editGuardian() {
    Guardian g = service.guardian(view.id("ID acudiente"));
    view.show(g);
    service.updateGuardian(
        new Guardian(
            g.id(),
            view.edit("Documento", g.document()),
            view.edit("Nombre", g.firstName()),
            view.edit("Apellido", g.lastName()),
            view.edit("Teléfono", g.phone()),
            view.edit("Dirección", g.address()),
            view.editFlag("¿Activo?", g.active())));
    view.show("Acudiente actualizado.");
  }
}
