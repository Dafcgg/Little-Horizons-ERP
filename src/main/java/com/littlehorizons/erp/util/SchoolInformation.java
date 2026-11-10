package com.littlehorizons.erp.util;

public record SchoolInformation(
    String name, String nit, String address, String phone, String email) {
  public static SchoolInformation fromEnvironment() {
    var env = System.getenv();
    return new SchoolInformation(
        env.getOrDefault("ERP_SCHOOL_NAME", "Colegio Internacional Little Horizons S.A.S. (DEMO)"),
        env.getOrDefault("ERP_SCHOOL_NIT", "900000000-0 (SIMULADO)"),
        env.getOrDefault("ERP_SCHOOL_ADDRESS", "Calle de demostracion 123, Bogota"),
        env.getOrDefault("ERP_SCHOOL_PHONE", "0000000000"),
        env.getOrDefault("ERP_SCHOOL_EMAIL", "colegio@example.invalid"));
  }
}
