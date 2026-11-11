package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataInputStream;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MavenStructureTest {
  @Test
  void productionSourcesAndJava17BytecodeUseRequiredMavenLayout() throws Exception {
    Path root = Path.of("src/main/java/com/littlehorizons/erp");
    for (String layer : List.of("model", "view", "controller", "service", "dao", "util"))
      assertTrue(Files.isDirectory(root.resolve(layer)), layer);
    assertTrue(Files.isRegularFile(root.resolve("Main.java")));
    try (var files = Files.walk(Path.of("src/main/java"))) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        assertTrue(file.startsWith(root), file.toString());
        String packageName =
            Path.of("src/main/java")
                .relativize(file.getParent())
                .toString()
                .replace(java.io.File.separatorChar, '.');
        assertTrue(
            Files.readString(file).contains("package " + packageName + ";"), file.toString());
      }
    }
    try (var input =
        new DataInputStream(
            Files.newInputStream(Path.of("target/classes/com/littlehorizons/erp/Main.class")))) {
      assertEquals(0xCAFEBABE, input.readInt());
      input.readUnsignedShort();
      assertEquals(61, input.readUnsignedShort());
    }
  }
}
