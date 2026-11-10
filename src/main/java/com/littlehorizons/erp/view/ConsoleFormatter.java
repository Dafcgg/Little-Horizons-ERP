package com.littlehorizons.erp.view;

import com.littlehorizons.erp.model.*;
import java.util.stream.Collectors;

final class ConsoleFormatter {
  private ConsoleFormatter() {}

  private static String yes(boolean value) {
    return value ? "sí" : "no";
  }

  private static String type(BorrowerType type) {
    return type == BorrowerType.STUDENT ? "estudiante" : "profesor";
  }

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
          + yes(g.active());
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
          + yes(s.active());
    if (value instanceof GuardianLink l)
      return "Estudiante: "
          + l.studentId()
          + " | Acudiente: "
          + l.guardianId()
          + " | "
          + l.relationship()
          + " | Principal: "
          + yes(l.primary())
          + " | Emergencia: "
          + yes(l.emergency())
          + " | Responsable financiero: "
          + yes(l.financial());
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
          + yes(a.active());
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
    if (value instanceof Club c)
      return "Club #"
          + c.id()
          + " | "
          + c.name()
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
    if (value instanceof PaymentConcept c)
      return "Concepto #"
          + c.id()
          + " | "
          + c.code()
          + " | "
          + c.name()
          + " | Base COP "
          + c.amount()
          + " | Tasa IVA: "
          + c.taxRate()
          + " | Activo: "
          + yes(c.active());
    if (value instanceof StudentFee f)
      return "Estudiante: "
          + f.studentId()
          + " | Concepto: "
          + f.conceptId()
          + " | Tarifa COP "
          + f.amount();
    if (value instanceof ObligationEntry o)
      return "Obligación #"
          + o.id()
          + " | Estudiante: "
          + o.studentId()
          + " | Concepto: "
          + o.conceptId()
          + " | Referencia: "
          + o.reference()
          + " | Vencimiento: "
          + o.dueDate()
          + " | Importe COP "
          + o.amount()
          + " | Tasa IVA: "
          + o.taxRate()
          + " | Estado: "
          + o.status();
    if (value instanceof PaymentEntry p) {
      PaymentRequest r = p.request();
      return "Pago #"
          + p.id()
          + " | Estudiante: "
          + p.studentId()
          + " | Obligación: "
          + r.obligationId()
          + " | Acudiente: "
          + r.guardianId()
          + " | COP "
          + r.amount()
          + " | Fecha: "
          + r.date()
          + " | Método: "
          + r.method().databaseValue()
          + " | Recibo: "
          + r.receipt()
          + " | Estado: "
          + p.status()
          + " | Motivo: "
          + java.util.Objects.toString(p.cancellationReason(), "—")
          + " | Pago corregido: "
          + java.util.Objects.toString(p.originalPaymentId(), "—");
    }
    if (value instanceof Invoice i)
      return "Factura #"
          + i.id()
          + " | "
          + i.number()
          + " | Pago: "
          + i.paymentId()
          + " | Fecha: "
          + i.date()
          + " | Estado: "
          + i.businessStatus()
          + " | PDF: "
          + i.status()
          + "\nEmisor: "
          + i.schoolName()
          + " | NIT: "
          + i.schoolNit()
          + " | "
          + i.schoolAddress()
          + " | "
          + i.schoolPhone()
          + " | "
          + i.schoolEmail()
          + "\nResponsable: "
          + i.customerName()
          + " | Documento: "
          + i.customerDocument()
          + " | Dirección: "
          + i.customerAddress()
          + "\nDetalle: "
          + i.lines().stream()
              .map(
                  l ->
                      l.description()
                          + " x "
                          + l.quantity()
                          + " | Unitario COP "
                          + l.unitPrice()
                          + " | Subtotal COP "
                          + l.subtotal()
                          + " | IVA COP "
                          + l.tax())
              .collect(Collectors.joining("; "))
          + "\nSubtotal COP "
          + i.subtotal()
          + " | IVA COP "
          + i.tax()
          + " | Total COP "
          + i.total()
          + "\nCUFE simulado: "
          + i.cufe()
          + "\nArchivo: "
          + java.util.Objects.toString(i.path(), "Pendiente")
          + " | Motivo anulación: "
          + java.util.Objects.toString(i.cancellationReason(), "—");
    if (value instanceof PaymentResult r)
      return "Pago #"
          + r.paymentId()
          + " | Factura #"
          + r.invoiceId()
          + " | PDF generado: "
          + yes(r.pdfGenerated())
          + " | "
          + r.message();
    if (value instanceof Balance b)
      return "Obligación #"
          + b.obligationId()
          + " | Estudiante: "
          + b.studentId()
          + " | "
          + b.concept()
          + " | Vencimiento: "
          + b.dueDate()
          + " | Saldo COP "
          + b.balance();
    if (value instanceof CatalogEntry c)
      return "Registro #"
          + c.id()
          + " | "
          + c.name()
          + " | Nivel: "
          + c.level()
          + " | Activo: "
          + yes(c.active());
    return String.valueOf(value);
  }
}
