package com.littlehorizons.erp.service;

import com.littlehorizons.erp.dao.ResourceDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.*;
import java.time.*;
import java.util.*;

public final class ResourceService extends TransactionalService {
  private final ResourceDao dao;

  public ResourceService(ConnectionProvider connections, ResourceDao dao) {
    super(connections);
    this.dao = dao;
  }

  public long create(SchoolResource resource) {
    require(resource != null, "Datos obligatorios.");
    text(resource.code(), "Código", 50);
    text(resource.name(), "Nombre", 150);
    text(resource.location(), "Ubicación", 100);
    return transaction(
        c -> {
          require(
              dao.activeCategory(c, resource.categoryId()), "Categoría inactiva o inexistente.");
          return dao.create(c, resource);
        });
  }

  public List<SchoolResource> resources() {
    return transaction(dao::resources);
  }

  public long lend(long resource, long borrower, BorrowerType type, LocalDate due) {
    require(
        type != null && isDatabaseDate(due) && !due.isBefore(LocalDate.now()),
        "Tipo o fecha de devolución inválida.");
    return transaction(
        c -> {
          SchoolResource item =
              dao.lock(c, resource)
                  .orElseThrow(() -> new BusinessException("Recurso inexistente."));
          require("Disponible".equals(item.status()), "El recurso no está disponible.");
          require(dao.activeBorrower(c, borrower, type), "Solicitante inactivo o inexistente.");
          long id = dao.createLoan(c, resource, borrower, type, LocalDate.now(), due);
          dao.setStatus(c, resource, "Prestado");
          return id;
        });
  }

  public void returnLoan(long id, BorrowerType type) {
    require(type != null, "Tipo de solicitante obligatorio.");
    transaction(
        c -> {
          Loan candidate =
              dao.findLoan(c, id, type)
                  .orElseThrow(() -> new BusinessException("Préstamo activo inexistente."));
          // Lock the resource first, consistently with lending and other returns.
          dao.lock(c, candidate.resourceId())
              .orElseThrow(() -> new BusinessException("Recurso inexistente."));
          Loan loan =
              dao.lockLoan(c, id, type)
                  .orElseThrow(() -> new BusinessException("El préstamo ya fue devuelto."));
          dao.returnLoan(c, id, type, LocalDate.now());
          dao.setStatus(c, loan.resourceId(), "Disponible");
          return null;
        });
  }

  public void changeStatus(long id, String status) {
    require(
        status != null && Set.of("Disponible", "En Mantenimiento", "Dado de Baja").contains(status),
        "Estado inválido.");
    transaction(
        c -> {
          SchoolResource item =
              dao.lock(c, id).orElseThrow(() -> new BusinessException("Recurso inexistente."));
          require(!"Prestado".equals(item.status()), "Primero debe registrar la devolución.");
          dao.setStatus(c, id, status);
          return null;
        });
  }

  public List<Loan> overdue() {
    LocalDate today = LocalDate.now();
    return transaction(
        c -> {
          List<LoanEntry> loans = new ArrayList<>();
          for (BorrowerType type : BorrowerType.values()) loans.addAll(dao.loans(c, type));
          return loans.stream()
              .filter(loan -> loan.returned() == null)
              .filter(loan -> Set.of("Activo", "Vencido").contains(loan.status()))
              .filter(loan -> loan.due().isBefore(today))
              .map(
                  loan ->
                      new Loan(
                          loan.id(), loan.resourceId(), loan.borrowerId(), loan.type(), loan.due()))
              .toList();
        });
  }

  public SchoolResource resource(long id) {
    return transaction(
        c -> dao.lock(c, id).orElseThrow(() -> new BusinessException("Recurso inexistente.")));
  }

  public void updateResource(SchoolResource resource) {
    require(resource != null, "Recurso obligatorio.");
    text(resource.code(), "Código", 50);
    text(resource.name(), "Nombre", 150);
    text(resource.location(), "Ubicación", 100);
    transaction(
        c -> {
          dao.lock(c, resource.id())
              .orElseThrow(() -> new BusinessException("Recurso inexistente."));
          require(
              dao.activeCategory(c, resource.categoryId()), "Categoría inactiva o inexistente.");
          dao.updateResource(c, resource);
          return null;
        });
  }

  public void deactivateResource(long id) {
    changeStatus(id, "Dado de Baja");
  }

  public List<LoanEntry> loans(BorrowerType type) {
    require(type != null, "Tipo obligatorio.");
    return transaction(c -> dao.loans(c, type));
  }

  public LoanEntry loan(long id, BorrowerType type) {
    require(type != null, "Tipo obligatorio.");
    return transaction(
        c ->
            dao.loanEntry(c, id, type, false)
                .orElseThrow(() -> new BusinessException("Préstamo inexistente.")));
  }

  public void updateDueDate(long id, BorrowerType type, LocalDate due) {
    require(type != null && isDatabaseDate(due), "Datos obligatorios.");
    transaction(
        c -> {
          LoanEntry initial =
              dao.loanEntry(c, id, type, false)
                  .orElseThrow(() -> new BusinessException("Préstamo inexistente."));
          dao.lock(c, initial.resourceId()).orElseThrow();
          LoanEntry old = dao.loanEntry(c, id, type, true).orElseThrow();
          require(old.returned() == null, "El préstamo ya está cerrado.");
          require(
              !due.isBefore(old.borrowed()), "El vencimiento no puede ser anterior al préstamo.");
          dao.updateDueDate(c, id, type, due);
          return null;
        });
  }

  public void cancelLoan(long id, BorrowerType type, String reason) {
    text(reason, "Motivo");
    textByteLength(reason, "El motivo", 65_535);
    require(type != null, "Tipo obligatorio.");
    transaction(
        c -> {
          LoanEntry initial =
              dao.loanEntry(c, id, type, false)
                  .orElseThrow(() -> new BusinessException("Préstamo inexistente."));
          dao.lock(c, initial.resourceId()).orElseThrow();
          LoanEntry old = dao.loanEntry(c, id, type, true).orElseThrow();
          require(old.returned() == null, "El préstamo ya está cerrado.");
          require(
              dao.cancelLoan(c, id, type, LocalDate.now(), reason) == 1,
              "El préstamo ya está cerrado.");
          dao.setStatus(c, old.resourceId(), "Disponible");
          return null;
        });
  }
}
