package com.littlehorizons.erp.model;

import java.nio.file.Path;
import java.util.Map;

public record AttendanceImportResult(long total, long successful, long failed, long skipped,
    Map<AttendanceStatus, Long> byStatus, Path report) {
  public AttendanceImportResult {
    byStatus = Map.copyOf(byStatus);
  }

  public double effectiveness() {
    return total == 0 ? 0.0 : 100.0 * successful / total;
  }

  public long count(AttendanceStatus status) {
    return byStatus.getOrDefault(status, 0L);
  }
}
