package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.StudentService;
import com.littlehorizons.erp.view.ConsoleView;

public final class StudentController {
  private final StudentService service;
  private final ConsoleView view;

  public StudentController(StudentService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Estudiantes y acudientes",
        new Action("Listar estudiantes", () -> view.showAll(service.students())),
        new Action("Listar acudientes", () -> view.showAll(service.guardians())),
        new Action(
            "Registrar acudiente",
            () -> view.show("Acudiente: " + service.registerGuardian(readGuardian()))),
        new Action(
            "Registrar estudiante con acudiente existente",
            () -> {
              Student student = readStudent();
              view.show(
                  "Estudiante: "
                      + service.register(
                          student, view.id("ID acudiente"), view.required("Parentesco")));
            }),
        new Action(
            "Registrar estudiante y acudiente nuevos",
            () -> {
              Student student = readStudent();
              Guardian guardian = readGuardian();
              view.show(
                  "Estudiante: "
                      + service.registerWithGuardian(
                          student, guardian, view.required("Parentesco")));
            }),
        new Action(
            "Matricular por grado y año",
            () -> {
              service.enroll(view.id("ID estudiante"), view.id("ID grado"), view.year());
              view.show("Matrícula registrada.");
            }));
  }

  private Student readStudent() {
    return new Student(
        0,
        view.required("Documento estudiante"),
        view.required("Nombre"),
        view.required("Apellido"),
        view.date("Nacimiento"),
        view.required("Dirección"),
        "Activo");
  }

  private Guardian readGuardian() {
    return new Guardian(
        0,
        view.required("Documento acudiente"),
        view.required("Nombre"),
        view.required("Apellido"),
        view.required("Teléfono de emergencia"),
        view.required("Dirección"));
  }
}
