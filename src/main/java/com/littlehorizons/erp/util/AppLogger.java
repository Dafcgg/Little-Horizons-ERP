package com.littlehorizons.erp.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.*;

/** Writes application diagnostics to a single UTF-8 file handler per process. */
public final class AppLogger {
  private static final Logger LOGGER = Logger.getLogger("com.littlehorizons.erp");

  private AppLogger() {}

  public static synchronized void configure() throws IOException {
    if (LOGGER.getHandlers().length == 0) {
      FileHandler handler =
          new FileHandler(System.getProperty("erp.log.path", "error_log.txt"), 1_000_000, 1, true);
      try {
        handler.setEncoding("UTF-8");
        LOGGER.setLevel(Level.FINE);
        handler.setLevel(Level.FINE);
        handler.setFormatter(new FileLogFormatter());
        LOGGER.setUseParentHandlers(false);
        LOGGER.addHandler(handler);
      } catch (IOException | RuntimeException error) {
        handler.close();
        throw error;
      }
    }
  }

  public static void info(String operation) {
    LOGGER.log(Level.INFO, operation);
  }

  public static void warning(String operation, Throwable error) {
    LOGGER.log(Level.WARNING, operation, error);
  }

  public static void error(String operation, Throwable error) {
    LOGGER.log(Level.SEVERE, operation, error);
  }

  public static void transactionRejected(BusinessException error) {
    LOGGER.log(Level.FINE, "Database transaction rejected by a business rule", error);
  }

  /** Uses a UTC timestamp and the same level names regardless of host locale. */
  private static final class FileLogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
      String header = "%s [%s] %s%n".formatted(
          record.getInstant(), record.getLevel().getName(), formatMessage(record));
      if (record.getThrown() == null) return header;

      var trace = new StringWriter();
      record.getThrown().printStackTrace(new PrintWriter(trace));
      return header + trace;
    }
  }
}
