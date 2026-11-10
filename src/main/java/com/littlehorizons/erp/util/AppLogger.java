package com.littlehorizons.erp.util;

import java.io.IOException;
import java.util.logging.*;

public final class AppLogger {
  private static final Logger LOGGER = Logger.getLogger("com.littlehorizons.erp");

  private AppLogger() {}

  public static synchronized void configure() throws IOException {
    if (LOGGER.getHandlers().length == 0) {
      FileHandler handler = new FileHandler("error_log.txt", 1_000_000, 1, true);
      try {
        handler.setEncoding("UTF-8");
        handler.setFormatter(new SimpleFormatter());
        LOGGER.setUseParentHandlers(false);
        LOGGER.addHandler(handler);
      } catch (IOException | RuntimeException error) {
        handler.close();
        throw error;
      }
    }
  }

  public static void error(String operation, Throwable error) {
    LOGGER.log(Level.SEVERE, operation, error);
  }
}
