package com.littlehorizons.erp.view;

import com.littlehorizons.erp.model.*;

/** Selects domain-specific formatting and handles simple messages and catalog values. */
final class ConsoleFormatter {
  private ConsoleFormatter() {}

  static String yes(boolean value) {
    return value ? "sí" : "no";
  }

  private static String type(BorrowerType type) {
    return type == BorrowerType.STUDENT ? "estudiante" : "profesor";
  }

  static String format(Object value) {
    if (value instanceof Club c)
      return "Club #"
          + c.id()
          + " | "
          + c.name()
          + " | Descripción: "
          + java.util.Objects.toString(c.description(), "")
          + " | Profesor: "
          + c.teacherId()
          + " | Cupo: "
          + c.capacity()
          + " | Activo: "
          + yes(c.active());
    if (value instanceof ClubMembership m)
      return "Membresía #"
          + m.id()
          + " | Club: "
          + m.clubId()
          + " | Estudiante: "
          + m.studentId()
          + " | Ingreso: "
          + m.joined()
          + " | Estado: "
          + m.status();
    if (value instanceof SchoolEvent e)
      return "Evento #"
          + e.id()
          + " | "
          + e.name()
          + " | Descripción: "
          + java.util.Objects.toString(e.description(), "")
          + " | Fecha: "
          + e.date()
          + " | Tipo: "
          + e.type()
          + " | Competencia: "
          + yes(e.competitive())
          + " | Activo: "
          + yes(e.active());
    if (value instanceof EventParticipation p)
      return "Evento: "
          + p.eventId()
          + " | Estudiante: "
          + p.studentId()
          + " | Posición: "
          + (p.position() == null || p.position().isBlank() ? "Sin posición" : p.position());
    if (value instanceof SchoolResource r)
      return "Recurso #"
          + r.id()
          + " | "
          + r.code()
          + " | "
          + r.name()
          + " | Categoría: "
          + r.categoryId()
          + " | Ubicación: "
          + r.location()
          + " | Estado: "
          + r.status();
    if (value instanceof LoanEntry l)
      return "Préstamo #"
          + l.id()
          + " | Tipo: "
          + type(l.type())
          + " | Solicitante: "
          + l.borrowerId()
          + " | Recurso: "
          + l.resourceId()
          + " | Inicio: "
          + l.borrowed()
          + " | Vencimiento: "
          + l.due()
          + " | Devolución: "
          + (l.returned() == null ? "Pendiente" : l.returned())
          + " | Estado: "
          + l.status();
    if (value instanceof Loan l)
      return "Préstamo #"
          + l.id()
          + " | Tipo: "
          + type(l.borrowerType())
          + " | Solicitante: "
          + l.borrowerId()
          + " | Recurso: "
          + l.resourceId()
          + " | Vencimiento: "
          + l.dueDate();
    if (value instanceof CatalogEntry c)
      return "Registro #"
          + c.id()
          + " | "
          + c.name()
          + " | Nivel: "
          + c.level()
          + " | Activo: "
          + yes(c.active());
    String academic = AcademicConsoleFormatter.format(value);
    if (academic != null) return academic;
    String financial = FinanceConsoleFormatter.format(value);
    return financial != null ? financial : String.valueOf(value);
  }
}
