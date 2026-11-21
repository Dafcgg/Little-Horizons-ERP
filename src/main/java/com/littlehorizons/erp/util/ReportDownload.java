package com.littlehorizons.erp.util;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Copies a completed report to the user's downloads directory. */
public final class ReportDownload {
  private final Path directoryOverride;

  public ReportDownload() {
    this(null);
  }

  public ReportDownload(Path downloadsDirectory) {
    this.directoryOverride = downloadsDirectory;
  }

  public Optional<Path> copy(Path report) {
    try {
      Path directory = directoryOverride == null
          ? DownloadsDirectoryResolver.resolve(Path.of(System.getProperty("user.home")))
          : Files.createDirectories(directoryOverride);
      Path destination = directory.toAbsolutePath().normalize().resolve(report.getFileName());
      if (!Files.exists(destination) || !Files.isSameFile(report, destination)) {
        copyNewFile(report, destination);
      }
      return Optional.of(destination);
    } catch (IOException | SecurityException failure) {
      AppLogger.warning("Could not copy attendance report to Downloads", failure);
      return Optional.empty();
    }
  }

  private static void copyNewFile(Path report, Path destination) throws IOException {
    // Only clean up a file this invocation successfully created.
    var output = Files.newOutputStream(destination, CREATE_NEW, WRITE);
    try (output) {
      Files.copy(report, output);
    } catch (IOException | SecurityException failure) {
      try {
        Files.deleteIfExists(destination);
      } catch (IOException | SecurityException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }
}
