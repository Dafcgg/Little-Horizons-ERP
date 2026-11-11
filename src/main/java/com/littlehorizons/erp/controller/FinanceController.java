package com.littlehorizons.erp.controller;

import static com.littlehorizons.erp.view.ConsoleView.Action;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.FinanceService;
import com.littlehorizons.erp.view.ConsoleView;

public final class FinanceController {
  private final FinanceService service;
  private final ConsoleView view;

  public FinanceController(FinanceService service, ConsoleView view) {
    this.service = service;
    this.view = view;
  }

  public void run() {
    view.menu(
        "Finanzas y facturas de demostración",
        new Action("Listar conceptos", () -> view.showAll(service.concepts())),
        new Action(
            "Crear concepto",
            () ->
                view.show(
                    "Concepto: "
                        + service.createConcept(
                            view.required("Código"),
                            view.required("Nombre"),
                            view.decimal("Valor base COP"),
                            view.decimal("Tasa IVA (0 o fracción, ej. 0.19)")))),
        new Action(
            "Definir tarifa particular",
            () -> {
              service.setFee(
                  view.id("ID estudiante"),
                  view.id("ID concepto"),
                  view.decimal("Valor particular COP antes de IVA"));
              view.show("Tarifa guardada para próximos cobros.");
            }),
        new Action(
            "Crear obligación de pago",
            () ->
                view.show(
                    "Obligación: "
                        + service.createObligation(
                            view.id("ID estudiante"),
                            view.id("ID concepto"),
                            view.date("Vencimiento"),
                            view.required("Referencia única del cobro, ej. 2026-11")))),
        new Action(
            "Confirmar pago y generar factura",
            () ->
                view.show(
                    service.confirmPayment(
                        new PaymentRequest(
                            view.id("ID obligación"),
                            view.id("ID acudiente"),
                            view.decimal("Monto pagado COP"),
                            view.date("Fecha pago"),
                            view.choice("Método", PaymentMethod.class),
                            view.required("Número de recibo (reutilizar en reintentos)"))))),
        new Action(
            "Regenerar PDF sin registrar otro pago",
            () -> view.show(service.regenerate(view.id("ID factura")))),
        new Action(
            "Historial por estudiante",
            () -> view.showAll(service.history(view.id("ID estudiante"), false))),
        new Action(
            "Historial familiar por acudiente",
            () -> view.showAll(service.history(view.id("ID acudiente"), true))),
        new Action("Mora de más de un mes calendario", () -> view.showAll(service.overdue())));
  }
}
