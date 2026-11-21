package com.littlehorizons.erp.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class DownloadsDirectoryResolver {
  private DownloadsDirectoryResolver() {}

  static Path resolve(Path home) throws IOException {
    Path downloads = home.resolve("Downloads");
    if (Files.isDirectory(downloads)) return downloads;

    Path localized = home.resolve("Descargas");
    if (Files.isDirectory(localized)) return localized;

    return Files.createDirectories(downloads);
  }
}
