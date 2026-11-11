package com.littlehorizons.erp.service;

import com.littlehorizons.erp.dao.CatalogDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.*;
import java.util.*;

public final class CatalogService extends TransactionalService {
  private final CatalogDao dao;

  public CatalogService(ConnectionProvider provider, CatalogDao dao) {
    super(provider);
    this.dao = dao;
  }

  private void validate(CatalogType type, CatalogEntry entry) {
    require(type != null && entry != null, "Datos de catálogo obligatorios.");
    text(entry.name(), "Nombre");
    require(entry.name().length() <= 50, "Nombre demasiado largo.");
    require(
        type != CatalogType.GRADE || entry.level() >= 0 && entry.level() <= 11,
        "Nivel inválido: use 0–11.");
  }

  public long create(CatalogType type, CatalogEntry entry) {
    validate(type, entry);
    return transaction(c -> dao.create(c, type, entry));
  }

  public List<CatalogEntry> list(CatalogType type) {
    require(type != null, "Catálogo obligatorio.");
    return transaction(c -> dao.list(c, type));
  }

  public CatalogEntry get(CatalogType type, long id) {
    return list(type).stream()
        .filter(e -> e.id() == id)
        .findFirst()
        .orElseThrow(() -> new BusinessException("Registro inexistente."));
  }

  public void update(CatalogType type, CatalogEntry entry) {
    validate(type, entry);
    transaction(
        c -> {
          CatalogEntry old =
              dao.lock(c, type, entry.id())
                  .orElseThrow(() -> new BusinessException("Registro inexistente."));
          require(
              type != CatalogType.GRADE
                  || old.level() == entry.level()
                  || !dao.hasReferences(c, type, entry.id()),
              "No se puede cambiar el nivel de un grado con historial.");
          require(dao.update(c, type, entry) == 1, "Registro inexistente.");
          return null;
        });
  }

  public void deactivate(CatalogType type, long id) {
    CatalogEntry e = get(type, id);
    update(type, new CatalogEntry(id, e.name(), e.level(), false));
  }
}
