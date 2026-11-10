package com.littlehorizons.erp.model;

public record SchoolResource(
    long id, String code, String name, long categoryId, String location, String status) {}
