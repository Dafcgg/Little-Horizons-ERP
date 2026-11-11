package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.FinanceService;
import com.littlehorizons.erp.view.ConsoleView;

public final class FinanceAdministrationController {
  private final FinanceService service;
  private final ConsoleView view;

  public FinanceAdministrationController(FinanceService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Edición financiera y anulaciones",
        new Action("Listar conceptos y estados", () -> view.showAll(service.conceptEntries())),
        new Action("Editar concepto / reactivar", this::editConcept),
        new Action(
            "Inactivar concepto",
            () -> {
              service.deactivateConcept(view.id("ID concepto"));
              view.show("Concepto inactivado para nuevos cobros.");
            }),
        new Action(
            "Consultar tarifas particulares",
            () -> view.showAll(service.fees(view.id("ID estudiante")))),
        new Action(
            "Eliminar tarifa particular",
            () -> {
              service.removeFee(view.id("ID estudiante"), view.id("ID concepto"));
              view.show("Tarifa eliminada; próximos cobros usarán el valor base.");
            }),
        new Action("Listar obligaciones y estados", () -> view.showAll(service.obligations())),
        new Action(
            "Consultar obligación", () -> view.show(service.obligation(view.id("ID obligación")))),
        new Action("Editar obligación", this::editObligation),
        new Action(
            "Anular obligación",
            () -> {
              service.cancelObligation(view.id("ID obligación"));
              view.show("Obligación anulada.");
            }),
        new Action("Listar pagos y estados", () -> view.showAll(service.payments())),
        new Action("Consultar pago", () -> view.show(service.payment(view.id("ID pago")))),
        new Action("Corregir pago mediante anulación y reemplazo", this::correctPayment),
        new Action(
            "Anular pago y factura",
            () -> view.show(service.voidPayment(view.id("ID pago"), view.required("Motivo")))),
        new Action("Listar facturas y estados", () -> view.showAll(service.invoices())),
        new Action("Consultar factura", () -> view.show(service.invoice(view.id("ID factura")))),
        new Action(
            "Corregir factura mediante reemplazo",
            () -> {
              Invoice invoice = service.invoice(view.id("ID factura"));
              correctPayment(invoice.paymentId());
            }),
        new Action("Regenerar factura", () -> view.show(service.regenerate(view.id("ID factura")))),
        new Action(
            "Anular factura y su pago",
            () -> view.show(service.voidInvoice(view.id("ID factura"), view.required("Motivo")))));
  }

  private void editConcept() {
    PaymentConcept c = service.concept(view.id("ID concepto"));
    view.show(c);
    service.updateConcept(
        new PaymentConcept(
            c.id(),
            view.edit("Código", c.code()),
            view.edit("Nombre", c.name()),
            view.editDecimal("Monto base", c.amount()),
            view.editDecimal("Tasa IVA", c.taxRate()),
            view.editFlag("¿Activo?", c.active())));
    view.show("Concepto actualizado.");
  }

  private void editObligation() {
    ObligationEntry e = service.obligation(view.id("ID obligación"));
    view.show(e);
    service.updateObligation(
        e.id(),
        view.edit("Referencia", e.reference()),
        view.editDate("Vencimiento", e.dueDate()),
        view.editDecimal("Importe total COP", e.amount()));
    view.show("Obligación actualizada.");
  }

  private void correctPayment() {
    correctPayment(view.id("ID pago original"));
  }

  private void correctPayment(long paymentId) {
    PaymentEntry e = service.payment(paymentId);
    view.show(e);
    PaymentRequest p = e.request();
    var replacement =
        new PaymentRequest(
            p.obligationId(),
            view.editId("ID acudiente", p.guardianId()),
            view.editDecimal("Importe COP", p.amount()),
            view.editDate("Fecha pago", p.date()),
            view.choice("Método", PaymentMethod.class),
            view.required("Recibo NUEVO (reutilizar este mismo en reintentos)"));
    view.show(service.correctPayment(e.id(), replacement, view.required("Motivo de corrección")));
  }
}
