package com.littlehorizons.erp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadsDirectoryResolverTest {
  @TempDir Path home;

  @Test
  void prefersDownloadsWhenBothDirectoriesExist() throws Exception {
    Path downloads = Files.createDirectory(home.resolve("Downloads"));
    Files.createDirectory(home.resolve("Descargas"));

    assertEquals(downloads, DownloadsDirectoryResolver.resolve(home));
  }

  @Test
  void usesTheLocalizedDirectoryWhenItExists() throws Exception {
    Path localized = Files.createDirectory(home.resolve("Descargas"));

    assertEquals(localized, DownloadsDirectoryResolver.resolve(home));
    assertFalse(Files.exists(home.resolve("Downloads")));
  }

  @Test
  void createsDownloadsWhenNeitherDirectoryExists() throws Exception {
    Path downloads = home.resolve("Downloads");

    assertEquals(downloads, DownloadsDirectoryResolver.resolve(home));
    assertTrue(Files.isDirectory(downloads));
  }
}
