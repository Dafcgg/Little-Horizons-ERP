package com.littlehorizons.erp.view;

import com.littlehorizons.erp.model.*;

/** Formats students, guardians, and academic records for the Spanish console. */
final class AcademicConsoleFormatter {
  private AcademicConsoleFormatter() {}

  static String format(Object value) {
    if (value instanceof Student s)
      return "Estudiante #"
          + s.id()
          + " | "
          + s.firstName()
          + " "
          + s.lastName()
          + " | Documento: "
          + s.document()
          + " | Nacimiento: "
          + s.birthDate()
          + " | Dirección: "
          + s.address()
          + " | Estado: "
          + s.status();
    if (value instanceof Guardian g)
      return "Acudiente #"
          + g.id()
          + " | "
          + g.firstName()
          + " "
          + g.lastName()
          + " | Documento: "
          + g.document()
          + " | Teléfono: "
          + g.phone()
          + " | Dirección: "
          + g.address()
          + " | Activo: "
          + ConsoleFormatter.yes(g.active());
    if (value instanceof Teacher t)
      return "Profesor #"
          + t.id()
          + " | "
          + t.firstName()
          + " "
          + t.lastName()
          + " | Documento: "
          + t.document()
          + " | Correo: "
          + t.email()
          + " | Especialización: "
          + t.specialization()
          + " | Estado: "
          + t.status();
    if (value instanceof Subject s)
      return "Materia #"
          + s.id()
          + " | "
          + s.code()
          + " | "
          + s.name()
          + " | Grado: "
          + s.gradeId()
          + " | Activa: "
          + ConsoleFormatter.yes(s.active());
    if (value instanceof GuardianLink l)
      return "Estudiante: "
          + l.studentId()
          + " | Acudiente: "
          + l.guardianId()
          + " | "
          + l.relationship()
          + " | Principal: "
          + ConsoleFormatter.yes(l.primary())
          + " | Emergencia: "
          + ConsoleFormatter.yes(l.emergency())
          + " | Responsable financiero: "
          + ConsoleFormatter.yes(l.financial());
    if (value instanceof Enrollment e)
      return "Matrícula #"
          + e.id()
          + " | Estudiante: "
          + e.studentId()
          + " | Grado: "
          + e.gradeId()
          + " | Año: "
          + e.year()
          + " | Estado: "
          + e.status();
    if (value instanceof TeachingAssignment a)
      return "Asignación #"
          + a.id()
          + " | Profesor: "
          + a.teacherId()
          + " | Materia: "
          + a.subjectId()
          + " | Año: "
          + a.year()
          + " | Grupo: "
          + a.section()
          + " | Activa: "
          + ConsoleFormatter.yes(a.active());
    if (value instanceof ClassSchedule s)
      return "Horario #"
          + s.id()
          + " | Asignación: "
          + s.assignmentId()
          + " | Día (1=lunes): "
          + s.day()
          + " | "
          + s.start()
          + "–"
          + s.end();
    if (value instanceof Subscription s)
      return "Inscripción #"
          + s.id()
          + " | Estudiante: "
          + s.studentId()
          + " | Materia: "
          + s.subjectId()
          + " | Año: "
          + s.year()
          + " | Período: "
          + s.period().databaseValue()
          + " | Grupo: "
          + s.section()
          + " | Estado: "
          + s.status();
    if (value instanceof AcademicRecord r)
      return "Estudiante: "
          + r.studentId()
          + " | Materia: "
          + r.subjectId()
          + " | Profesor: "
          + r.teacherId()
          + " | Año: "
          + r.year()
          + " | Período: "
          + r.period().databaseValue()
          + " | Nota: "
          + r.grade();
    if (value instanceof AttendanceRecord a)
      return "Asistencia #"
          + a.id()
          + " | Estudiante: "
          + a.studentId()
          + " | Materia: "
          + a.subjectId()
          + " | Profesor: "
          + a.teacherId()
          + " | Fecha: "
          + a.date()
          + " | Año: "
          + a.year()
          + " | Período: "
          + a.period().databaseValue()
          + " | Estado: "
          + a.status().databaseValue();
    return null;
  }
}
