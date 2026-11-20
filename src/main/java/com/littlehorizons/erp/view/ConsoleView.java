package com.littlehorizons.erp.view;

import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Presents records and exposes shared prompts and menus to the controllers. */
public final class ConsoleView {
  private final ConsoleInput input;
  private final PrintStream output;

  public ConsoleView(InputStream input, PrintStream output) {
    this.input = new ConsoleInput(input, output);
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

  public void menu(String title, Action... actions) {
    ConsoleMenu.run(this, title, actions);
  }

  public String read(String label) {
    return input.read(label);
  }

  public void awaitEnter() {
    output.flush();
    input.awaitEnter();
  }

  public String required(String label) {
    return input.required(label);
  }

  public long id(String label) {
    return input.id(label);
  }

  public int integer(String label, int min, int max) {
    return input.integer(label, min, max);
  }

  public int year() {
    return input.year();
  }

  public BigDecimal decimal(String label) {
    return input.decimal(label);
  }

  /** Applies a side-effect-free rule while retrying the current field. */
  public BigDecimal decimal(String label, Consumer<BigDecimal> validation) {
    return input.decimal(label, validation);
  }

  public LocalDate date(String label) {
    return input.date(label);
  }

  public LocalTime time(String label) {
    return input.time(label);
  }

  public <E extends Enum<E>> E choice(String label, Class<E> type) {
    return input.choice(label, type);
  }

  public String edit(String label, String current) {
    return input.edit(label, current);
  }

  public long editId(String label, long current) {
    return input.editId(label, current);
  }

  public int editInteger(String label, int current, int min, int max) {
    return input.editInteger(label, current, min, max);
  }

  public LocalDate editDate(String label, LocalDate current) {
    return input.editDate(label, current);
  }

  public BigDecimal editDecimal(String label, BigDecimal current) {
    return input.editDecimal(label, current);
  }

  public boolean flag(String label) {
    return input.flag(label);
  }

  public boolean editFlag(String label, boolean current) {
    return input.editFlag(label, current);
  }

  public String option(String label, List<String> options) {
    return input.option(label, options);
  }
}
