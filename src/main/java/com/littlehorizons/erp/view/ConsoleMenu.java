package com.littlehorizons.erp.view;

import com.littlehorizons.erp.util.AppLogger;
import com.littlehorizons.erp.util.BusinessException;

/** Runs menu actions and keeps recoverable failures within the current menu. */
final class ConsoleMenu {
  private ConsoleMenu() {}

  static void run(ConsoleView view, String title, ConsoleView.Action... actions) {
    while (true) {
      view.show("\n=== " + title + " ===");
      for (int index = 0; index < actions.length; index++)
        view.show((index + 1) + ". " + actions[index].label());
      view.show("0. Volver / salir");
      try {
        int option = view.integer("Opción", 0, actions.length);
        if (option == 0) return;
        actions[option - 1].task().run();
      } catch (ConsoleView.Cancelled ignored) {
        view.show("Operación cancelada.");
      } catch (BusinessException error) {
        // The user can correct and resubmit the form explicitly after a service rejects it.
        view.show(error.getMessage());
      } catch (ConsoleView.EndOfInput error) {
        throw error;
      } catch (RuntimeException error) {
        AppLogger.error("Console operation failed", error);
        view.show("No se pudo completar la operación. Consulte error_log.txt.");
      }
    }
  }
}
