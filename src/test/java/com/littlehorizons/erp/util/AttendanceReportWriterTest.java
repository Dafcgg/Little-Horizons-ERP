package com.littlehorizons.erp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class AttendanceReportWriterTest {
  @Test
  void truncatesWideNamesWithoutShiftingTheFollowingColumns() throws Exception {
    var output = new StringWriter();
    AttendanceReportWriter.saved(output, 2, 45, "1 - " + "界".repeat(40),
        "2 - Matemáticas", "2026-02-03", "Presente");

    String[] cells = output.toString().split("│");
    assertEquals(" 1 - " + "界".repeat(15) + "…  ", cells[3]);
    assertEquals("2 - Matemáticas", cells[4].strip());
    assertEquals("2026-02-03", cells[5].strip());
  }
}
