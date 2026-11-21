package com.littlehorizons.erp.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.littlehorizons.erp.model.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Formats the coordinator's report in monospaced display columns. */
public final class AttendanceReportWriter {
  private static final Table SUMMARY = new Table(37, 14);
  private static final Table DISTRIBUTION = new Table(15, 10, 12, 20);
  private static final Table ERRORS = new Table(10, 43, 62);
  private static final Table RECORDS = new Table(10, 12, 36, 30, 10, 13);
  private static final Pattern GRAPHEME = Pattern.compile("\\X");
  private static final Pattern CONTROLS = Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}]");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss");

  private AttendanceReportWriter() {}

  public static void saved(Writer spool, long line, long id, String student, String subject,
      String date, String status) throws IOException {
    spool.write("S" + RECORDS.row(Long.toString(line), Long.toString(id), student, subject, date, status));
  }

  public static void rejected(Writer spool, long line, String raw, String reason, AttendanceNames names)
      throws IOException {
    List<String> data = wrap(raw, ERRORS.widths[1]);
    if (names.student() != null) data.add(fit("Estudiante: " + names.student(), ERRORS.widths[1]).stripTrailing());
    if (names.subject() != null) data.add(fit("Materia: " + names.subject(), ERRORS.widths[1]).stripTrailing());
    List<String> advice = wrap(reason, ERRORS.widths[2]);
    for (int i = 0; i < Math.max(data.size(), advice.size()); i++) {
      spool.write("E" + ERRORS.row(i == 0 ? Long.toString(line) : "",
          i < data.size() ? data.get(i) : "", i < advice.size() ? advice.get(i) : ""));
    }
  }

  public static void write(AttendanceImportResult result, Path spool, LocalDateTime started)
      throws IOException {
    SchoolInformation school = SchoolInformation.fromEnvironment();
    try (BufferedWriter out = Files.newBufferedWriter(result.report(), UTF_8)) {
      out.write("╔" + "═".repeat(70) + "╗\n");
      for (String line : List.of(school.name().toUpperCase(Locale.ROOT), "NIT: " + school.nit(),
          "REPORTE DE IMPORTACIÓN DE ASISTENCIAS", "Ejecución: " + TIME.format(started) + " (Bogotá)",
          "Archivo origen: asistencias_importar.txt", result.failed() == 0
              ? "[ ✔ IMPORTACIÓN EXITOSA SIN ERRORES ]" : "[ ⚠ IMPORTACIÓN PARCIAL — REQUIERE REVISIÓN ]")) {
        out.write("║ " + fit(line, 68) + " ║\n");
      }
      out.write("╚" + "═".repeat(70) + "╝\n\nRESUMEN EJECUTIVO\n");
      out.write(SUMMARY.top());
      out.write(SUMMARY.row("Total de registros evaluados", Long.toString(result.total())));
      out.write(SUMMARY.row("Líneas vacías omitidas", Long.toString(result.skipped())));
      out.write(SUMMARY.row("Registros guardados en base de datos", Long.toString(result.successful())));
      out.write(SUMMARY.row("Registros rechazados", Long.toString(result.failed())));
      out.write(SUMMARY.row("Porcentaje de efectividad", percent(result.effectiveness())));
      out.write(SUMMARY.bottom());
      if (result.total() == 0) out.write("El archivo no contenía registros de asistencia para guardar.\n");
      else if (result.failed() == 0) out.write("Todas las asistencias ya están visibles en el historial académico.\n");
      else out.write("Revise las filas rechazadas (%d) y vuelva a cargar solo las que necesiten corrección.\n"
          .formatted(result.failed()));

      out.write("\nDISTRIBUCIÓN POR ESTADO DE ASISTENCIA\nPorcentajes sobre las asistencias guardadas.\n");
      out.write(DISTRIBUTION.header("Estado", "Cantidad", "Porcentaje", "Gráfico"));
      for (AttendanceStatus status : AttendanceStatus.values()) {
        long count = result.count(status);
        double share = result.successful() == 0 ? 0 : (double) count / result.successful();
        int bars = (int) Math.round(share * 20);
        out.write(DISTRIBUTION.row(status.databaseValue(), Long.toString(count), percent(100 * share),
            "█".repeat(bars) + "░".repeat(20 - bars)));
      }
      out.write(DISTRIBUTION.bottom());
      out.write("\nAUDITORÍA DE REGISTROS RECHAZADOS\n");
      if (result.failed() == 0) {
        out.write("No se encontraron errores. El 100% de los registros cumplieron con el formato exigido.\n");
      } else {
        out.write(ERRORS.header("Línea TXT", "Dato ingresado", "¿Qué ocurrió y cómo corregirlo?"));
        append(out, spool, 'E');
        out.write(ERRORS.bottom());
      }
      out.write("\nDETALLE COMPLETO DE ASISTENCIAS REGISTRADAS\n");
      out.write(RECORDS.header("Línea", "ID Reg.", "Estudiante (ID - Nombre Completo)",
          "Materia (ID - Nombre)", "Fecha", "Estado"));
      append(out, spool, 'S');
      out.write(RECORDS.bottom());
      if (result.successful() == 0) out.write("Sin asistencias registradas.\n");
    }
  }

  private static void append(Writer out, Path path, char marker) throws IOException {
    try (BufferedReader entries = Files.newBufferedReader(path, UTF_8)) {
      String entry;
      while ((entry = entries.readLine()) != null) {
        if (entry.charAt(0) == marker) {
          out.write(entry, 1, entry.length() - 1);
          out.write('\n');
        }
      }
    }
  }

  private static String percent(double value) {
    return String.format(Locale.ROOT, "%.2f%%", value);
  }

  private static List<String> wrap(String value, int width) {
    var rows = new ArrayList<String>();
    var line = new StringBuilder();
    var glyphs = GRAPHEME.matcher(CONTROLS.matcher(value).replaceAll(" "));
    int used = 0;
    int breakIndex = 0;
    int breakWidth = 0;
    while (glyphs.find()) {
      String glyph = glyphs.group();
      int size = TextColumns.displayWidth(glyph);
      if (used + size > width) {
        boolean atSpace = breakIndex > 0 && used - breakWidth + size <= width;
        int end = atSpace ? breakIndex : line.length();
        rows.add(line.substring(0, end));
        line.delete(0, end);
        used = atSpace ? used - breakWidth : 0;
        breakIndex = 0;
      }
      line.append(glyph);
      used += size;
      if (glyph.equals(" ")) {
        breakIndex = line.length();
        breakWidth = used;
      }
    }
    rows.add(line.toString());
    return rows;
  }

  private static String fit(String value, int width) {
    var glyphs = GRAPHEME.matcher(CONTROLS.matcher(value).replaceAll(" "));
    var cell = new StringBuilder();
    int used = 0;
    while (glyphs.find()) {
      String glyph = glyphs.group();
      int size = TextColumns.displayWidth(glyph);
      if (used + size > width || (used + size == width && glyphs.end() < value.length())) {
        cell.append('…');
        used++;
        break;
      }
      cell.append(glyph);
      used += size;
    }
    return cell + " ".repeat(width - used);
  }

  private record Table(int... widths) {
    String row(String... values) {
      var cells = new StringJoiner(" │ ", "│ ", " │\n");
      for (int i = 0; i < widths.length; i++) cells.add(fit(values[i], widths[i]));
      return cells.toString();
    }

    String border(String left, String join, String right) {
      return Arrays.stream(widths).mapToObj(width -> "─".repeat(width + 2))
          .collect(Collectors.joining(join, left, right + "\n"));
    }

    String top() { return border("┌", "┬", "┐"); }
    String bottom() { return border("└", "┴", "┘"); }
    String header(String... labels) {
      return top() + row(labels) + border("├", "┼", "┤");
    }
  }
}
