package com.littlehorizons.erp.model;

import java.nio.file.Path;

public record AttendanceImportResult(long total, long successful, long failed, Path report) {}
