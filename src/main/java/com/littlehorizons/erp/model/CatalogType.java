package com.littlehorizons.erp.model;

public enum CatalogType {
  GRADE("grados", "id_grado", "nombre_grado"),
  RESOURCE_CATEGORY("categorias_recurso", "id_categoria", "nombre_categoria");
  private final String table, id, name;

  CatalogType(String table, String id, String name) {
    this.table = table;
    this.id = id;
    this.name = name;
  }

  public String table() {
    return table;
  }

  public String id() {
    return id;
  }

  public String nameColumn() {
    return name;
  }
}
