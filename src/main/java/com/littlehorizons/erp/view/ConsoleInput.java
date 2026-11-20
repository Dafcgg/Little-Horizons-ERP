package com.littlehorizons.erp.view;

import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.BusinessException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Function;

/** Reads one field at a time and keeps previous answers when the current value is rejected. */
final class ConsoleInput {
  private final Scanner scanner;
  private final PrintStream output;

  ConsoleInput(InputStream input, PrintStream output) {
    this.scanner = new Scanner(input, StandardCharsets.UTF_8);
    this.output = output;
  }

  String read(String label) {
    output.print(label + " (q: cancelar): ");
    output.flush();
    if (!scanner.hasNextLine()) throw new ConsoleView.EndOfInput();
    String value = scanner.nextLine().trim();
    if (value.equalsIgnoreCase("q")) throw new ConsoleView.Cancelled();
    return value;
  }

  void awaitEnter() {
    while (scanner.hasNextLine()) {
      if (scanner.nextLine().isEmpty()) return;
    }
    throw new ConsoleView.EndOfInput();
  }

  private <T> T parse(String label, Function<String, T> parser) {
    // Read outside the validation block so cancellation and EOF interrupt the entire form.
    while (true) {
      String value = read(label);
      try {
        return parser.apply(value);
      } catch (IllegalArgumentException | DateTimeException error) {
        output.println("Dato inválido. Intente de nuevo.");
      } catch (BusinessException error) {
        output.println(error.getMessage() + " Intente de nuevo.");
      }
    }
  }

  String required(String label) {
    return parse(
        label,
        value -> {
          if (value.isBlank()) throw new IllegalArgumentException();
          return value;
        });
  }

  long id(String label) {
    return parse(
        label,
        value -> {
          long id = Long.parseLong(value);
          if (id <= 0) throw new IllegalArgumentException();
          return id;
        });
  }

  int integer(String label, int min, int max) {
    return parse(
        label,
        value -> {
          int number = Integer.parseInt(value);
          if (number < min || number > max) throw new IllegalArgumentException();
          return number;
        });
  }

  int year() {
    return integer("Año académico", 2000, 2100);
  }

  BigDecimal decimal(String label) {
    return parse(label, ConsoleInput::decimalValue);
  }

  BigDecimal decimal(String label, Consumer<BigDecimal> validation) {
    return parse(
        label,
        value -> {
          BigDecimal result = decimalValue(value);
          validation.accept(result);
          return result;
        });
  }

  private static BigDecimal decimalValue(String value) {
    return new BigDecimal(value.replace(',', '.'));
  }

  LocalDate date(String label) {
    return parse(label + " (AAAA-MM-DD)", LocalDate::parse);
  }

  LocalTime time(String label) {
    return parse(label + " (HH:MM)", LocalTime::parse);
  }

  <E extends Enum<E>> E choice(String label, Class<E> type) {
    E[] values = type.getEnumConstants();
    for (int index = 0; index < values.length; index++)
      output.println((index + 1) + ". " + display(values[index]));
    return values[integer(label, 1, values.length) - 1];
  }

  String edit(String label, String current) {
    String value = read(label + " [" + Objects.toString(current, "") + "]");
    return value.isEmpty() ? current : value;
  }

  long editId(String label, long current) {
    return parse(
        label + " [" + current + "]",
        value -> {
          long result = value.isBlank() ? current : Long.parseLong(value);
          if (result <= 0) throw new IllegalArgumentException();
          return result;
        });
  }

  int editInteger(String label, int current, int min, int max) {
    return parse(
        label + " [" + current + "]",
        value -> {
          int result = value.isBlank() ? current : Integer.parseInt(value);
          if (result < min || result > max) throw new IllegalArgumentException();
          return result;
        });
  }

  LocalDate editDate(String label, LocalDate current) {
    return parse(
        label + " [" + current + "]",
        value -> value.isBlank() ? current : LocalDate.parse(value));
  }

  BigDecimal editDecimal(String label, BigDecimal current) {
    return parse(
        label + " [" + current + "]",
        value -> value.isBlank() ? current : decimalValue(value));
  }

  boolean flag(String label) {
    return integer(label + " (1=sí, 0=no)", 0, 1) == 1;
  }

  boolean editFlag(String label, boolean current) {
    return editInteger(label, current ? 1 : 0, 0, 1) == 1;
  }

  String option(String label, List<String> options) {
    for (int i = 0; i < options.size(); i++) output.println((i + 1) + ". " + options.get(i));
    return options.get(integer(label, 1, options.size()) - 1);
  }

  private String display(Enum<?> value) {
    if (value instanceof AcademicPeriod period)
      return period.databaseValue();
    if (value instanceof AttendanceStatus status)
      return status.databaseValue();
    if (value instanceof PaymentMethod method)
      return method.databaseValue();
    if (value instanceof BorrowerType type)
      return type == BorrowerType.STUDENT ? "Estudiante" : "Profesor";
    if (value instanceof CatalogType type)
      return type == CatalogType.GRADE
          ? "Grados"
          : "Categorías de recursos";
    return value.toString();
  }
}
