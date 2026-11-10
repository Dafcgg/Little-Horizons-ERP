package com.littlehorizons.erp.view;

import com.littlehorizons.erp.util.*;
import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

public final class ConsoleView {
  private final Scanner scanner;
  private final PrintStream output;

  public ConsoleView(InputStream input, PrintStream output) {
    this.scanner = new Scanner(input, "UTF-8");
    this.output = output;
  }

  public record Action(String label, Runnable task) {}

  public static final class Cancelled extends RuntimeException {}

  public static final class EndOfInput extends RuntimeException {}

  public void show(Object value) {
    output.println(ConsoleFormatter.format(value));
  }

  public void showAll(Collection<?> rows) {
    if (rows.isEmpty()) show("Sin registros.");
    else rows.forEach(this::show);
  }

  public String read(String label) {
    output.print(label + " (q: cancelar): ");
    if (!scanner.hasNextLine()) throw new EndOfInput();
    String value = scanner.nextLine().trim();
    if (value.equalsIgnoreCase("q")) throw new Cancelled();
    return value;
  }

  private <T> T parse(String label, Function<String, T> parser) {
    while (true) {
      String value = read(label);
      try {
        return parser.apply(value);
      } catch (IllegalArgumentException | DateTimeException error) {
        show("Dato inválido. Intente de nuevo.");
      }
    }
  }

  public String required(String label) {
    return parse(
        label,
        value -> {
          if (value.isBlank()) throw new IllegalArgumentException();
          return value;
        });
  }

  public long id(String label) {
    return parse(
        label,
        value -> {
          long id = Long.parseLong(value);
          if (id <= 0) throw new IllegalArgumentException();
          return id;
        });
  }

  public int integer(String label, int min, int max) {
    return parse(
        label,
        value -> {
          int number = Integer.parseInt(value);
          if (number < min || number > max) throw new IllegalArgumentException();
          return number;
        });
  }

  public int year() {
    return integer("Año académico", 2000, 2100);
  }

  public BigDecimal decimal(String label) {
    return parse(label, value -> new BigDecimal(value.replace(',', '.')));
  }

  public LocalDate date(String label) {
    return parse(label + " (AAAA-MM-DD)", LocalDate::parse);
  }

  public LocalTime time(String label) {
    return parse(label + " (HH:MM)", LocalTime::parse);
  }

  public <E extends Enum<E>> E choice(String label, Class<E> type) {
    E[] values = type.getEnumConstants();
    for (int index = 0; index < values.length; index++)
      show((index + 1) + ". " + display(values[index]));
    return values[integer(label, 1, values.length) - 1];
  }

  public void menu(String title, Action... actions) {
    while (true) {
      show("\n=== " + title + " ===");
      for (int index = 0; index < actions.length; index++)
        show((index + 1) + ". " + actions[index].label());
      show("0. Volver / salir");
      try {
        int option = integer("Opción", 0, actions.length);
        if (option == 0) return;
        actions[option - 1].task().run();
      } catch (Cancelled ignored) {
        show("Operación cancelada.");
      } catch (BusinessException error) {
        show(error.getMessage());
      } catch (EndOfInput error) {
        throw error;
      } catch (RuntimeException error) {
        AppLogger.error("Console operation failed", error);
        show("No se pudo completar la operación. Consulte error_log.txt.");
      }
    }
  }

  public String edit(String label, String current) {
    String value = read(label + " [" + java.util.Objects.toString(current, "") + "]");
    return value.isEmpty() ? current : value;
  }

  public long editId(String label, long current) {
    return parse(
        label + " [" + current + "]",
        value -> {
          long result = value.isBlank() ? current : Long.parseLong(value);
          if (result <= 0) throw new IllegalArgumentException();
          return result;
        });
  }

  public int editInteger(String label, int current, int min, int max) {
    return parse(
        label + " [" + current + "]",
        value -> {
          int result = value.isBlank() ? current : Integer.parseInt(value);
          if (result < min || result > max) throw new IllegalArgumentException();
          return result;
        });
  }

  public java.time.LocalDate editDate(String label, java.time.LocalDate current) {
    return parse(
        label + " [" + current + "]",
        value -> value.isBlank() ? current : java.time.LocalDate.parse(value));
  }

  public java.math.BigDecimal editDecimal(String label, java.math.BigDecimal current) {
    return parse(
        label + " [" + current + "]",
        value -> value.isBlank() ? current : new java.math.BigDecimal(value.replace(',', '.')));
  }

  public boolean flag(String label) {
    return integer(label + " (1=sí, 0=no)", 0, 1) == 1;
  }

  public boolean editFlag(String label, boolean current) {
    return editInteger(label, current ? 1 : 0, 0, 1) == 1;
  }

  public String option(String label, java.util.List<String> options) {
    for (int i = 0; i < options.size(); i++) show((i + 1) + ". " + options.get(i));
    return options.get(integer(label, 1, options.size()) - 1);
  }

  private String display(Enum<?> value) {
    if (value instanceof com.littlehorizons.erp.model.AcademicPeriod period)
      return period.databaseValue();
    if (value instanceof com.littlehorizons.erp.model.AttendanceStatus status)
      return status.databaseValue();
    if (value instanceof com.littlehorizons.erp.model.PaymentMethod method)
      return method.databaseValue();
    if (value instanceof com.littlehorizons.erp.model.BorrowerType type)
      return type == com.littlehorizons.erp.model.BorrowerType.STUDENT ? "Estudiante" : "Profesor";
    if (value instanceof com.littlehorizons.erp.model.CatalogType type)
      return type == com.littlehorizons.erp.model.CatalogType.GRADE
          ? "Grados"
          : "Categorías de recursos";
    return value.toString();
  }
}
