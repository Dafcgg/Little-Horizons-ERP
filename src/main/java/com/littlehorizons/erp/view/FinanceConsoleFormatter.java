package com.littlehorizons.erp.view;

import com.littlehorizons.erp.model.*;
import java.util.stream.Collectors;

/** Formats payment, balance, and invoice records for the Spanish console. */
final class FinanceConsoleFormatter {
  private FinanceConsoleFormatter() {}

  static String format(Object value) {
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
          + ConsoleFormatter.yes(c.active());
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
          + " | Estado del archivo: "
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
          + " | Archivo generado: "
          + ConsoleFormatter.yes(r.fileGenerated())
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
    return null;
  }
}
