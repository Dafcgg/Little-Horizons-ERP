package com.littlehorizons.erp.service;

import com.littlehorizons.erp.dao.StudentDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.ConnectionProvider;
import com.littlehorizons.erp.util.JdbcTransactionManager.Work;
import java.time.*;
import java.util.*;

/** Keeps student records, guardian links, and enrollment changes consistent. */
public final class StudentService extends TransactionalService {
  private final StudentDao dao;

  public StudentService(ConnectionProvider connections, StudentDao dao) {
    super(connections);
    this.dao = dao;
  }

  /** Creates the student and their first active guardian link atomically. */
  public long register(Student student, long guardianId, String relationship) {
    validate(student);
    text(relationship, "Parentesco", 50);
    return transaction(
        c -> {
          require(
              dao.lockGuardian(c, guardianId)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Acudiente inexistente."))
                  .active(),
              "Acudiente inactivo.");
          long id = dao.createStudent(c, student);
          dao.linkGuardian(c, id, guardianId, relationship);
          return id;
        });
  }

  public long registerWithGuardian(Student student, Guardian guardian, String relationship) {
    validate(student);
    validate(guardian);
    text(relationship, "Parentesco", 50);
    return transaction(
        c -> {
          long guardianId = dao.createGuardian(c, guardian);
          require(
              dao.lockGuardian(c, guardianId)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Acudiente inexistente."))
                  .active(),
              "Acudiente inactivo.");
          long id = dao.createStudent(c, student);
          dao.linkGuardian(c, id, guardianId, relationship);
          return id;
        });
  }

  private void validate(Student student) {
    require(student != null, "Datos obligatorios.");
    text(student.document(), "Documento", 20);
    text(student.firstName(), "Nombre", 100);
    text(student.lastName(), "Apellido", 100);
    textLength(student.address(), "Dirección", 255);
    require(
        isDatabaseDate(student.birthDate()) && student.birthDate().isBefore(LocalDate.now()),
        "Fecha de nacimiento inválida.");
  }

  private void validate(Guardian guardian) {
    require(guardian != null, "Datos obligatorios.");
    text(guardian.document(), "Documento", 20);
    text(guardian.firstName(), "Nombre", 100);
    text(guardian.lastName(), "Apellido", 100);
    text(guardian.phone(), "Teléfono", 20);
    textLength(guardian.address(), "Dirección", 255);
  }

  public long registerGuardian(Guardian guardian) {
    validate(guardian);
    return transaction(c -> dao.createGuardian(c, guardian));
  }

  public void enroll(long studentId, long gradeId, int year) {
    require(year >= 2000 && year <= 2100, "Año inválido.");
    transaction(
        c -> {
          require(dao.isActive(c, studentId), "Estudiante inexistente o inactivo.");
          require(dao.hasGuardian(c, studentId), "El estudiante necesita un acudiente.");
          require(dao.activeGrade(c, gradeId), "Grado inactivo o inexistente.");
          dao.enroll(c, studentId, gradeId, year);
          return null;
        });
  }

  public List<Student> students() {
    return transaction(dao::findStudents);
  }

  public List<Guardian> guardians() {
    return transaction(dao::findGuardians);
  }

  public Student student(long id) {
    return transaction(
        c ->
            dao.lockStudent(c, id)
                .orElseThrow(
                    () ->
                        new com.littlehorizons.erp.util.BusinessException(
                            "Estudiante inexistente.")));
  }

  public Guardian guardian(long id) {
    return transaction(
        c ->
            dao.lockGuardian(c, id)
                .orElseThrow(
                    () ->
                        new com.littlehorizons.erp.util.BusinessException(
                            "Acudiente inexistente.")));
  }

  public void updateStudent(Student student) {
    validate(student);
    require(
        student.status() != null
            && Set.of("Activo", "Retirado", "Graduado", "Suspendido").contains(student.status()),
        "Estado inválido.");
    transaction(
        c -> {
          dao.lockStudent(c, student.id())
              .orElseThrow(
                  () ->
                      new com.littlehorizons.erp.util.BusinessException("Estudiante inexistente."));
          require(
              dao.activeGuardians(c, student.id(), 0) > 0,
              "El estudiante necesita un acudiente activo.");
          require(dao.updateStudent(c, student) == 1, "Estudiante inexistente.");
          return null;
        });
  }

  public void deactivateStudent(long id) {
    Student s = student(id);
    updateStudent(
        new Student(
            s.id(),
            s.document(),
            s.firstName(),
            s.lastName(),
            s.birthDate(),
            s.address(),
            "Retirado"));
  }

  public void updateGuardian(Guardian guardian) {
    validate(guardian);
    transaction(
        c -> {
          for (long student : dao.guardianStudents(c, guardian.id())) {
            dao.lockStudent(c, student);
            if (!guardian.active())
              require(
                  dao.activeGuardians(c, student, guardian.id()) > 0,
                  "Otro acudiente activo debe asumir la responsabilidad antes de inactivar.");
          }
          dao.lockGuardian(c, guardian.id())
              .orElseThrow(
                  () ->
                      new com.littlehorizons.erp.util.BusinessException("Acudiente inexistente."));
          require(dao.updateGuardian(c, guardian) == 1, "Acudiente inexistente.");
          for (long student : dao.guardianStudents(c, guardian.id())) {
            dao.lockStudent(c, student);
            ensurePrincipal(student).execute(c);
          }
          return null;
        });
  }

  public void deactivateGuardian(long id) {
    Guardian g = guardian(id);
    updateGuardian(
        new Guardian(
            g.id(), g.document(), g.firstName(), g.lastName(), g.phone(), g.address(), false));
  }

  public List<GuardianLink> links(long student) {
    return transaction(c -> dao.links(c, student));
  }

  /** Serializes edits to guardian links and restores a principal contact when necessary. */
  public void saveLink(GuardianLink link) {
    require(link != null, "Vínculo obligatorio.");
    text(link.relationship(), "Parentesco", 50);
    transaction(
        c -> {
          dao.lockStudent(c, link.studentId())
              .orElseThrow(
                  () ->
                      new com.littlehorizons.erp.util.BusinessException("Estudiante inexistente."));
          require(
              dao.lockGuardian(c, link.guardianId())
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Acudiente inexistente."))
                  .active(),
              "Acudiente inactivo.");
          dao.saveLink(c, link);
          ensurePrincipal(link.studentId()).execute(c);
          return null;
        });
  }

  /** Removes the relationship only if another active guardian remains linked. */
  public void removeLink(long student, long guardian) {
    transaction(
        c -> {
          dao.lockStudent(c, student)
              .orElseThrow(
                  () ->
                      new com.littlehorizons.erp.util.BusinessException("Estudiante inexistente."));
          require(
              dao.activeGuardians(c, student, guardian) > 0,
              "No se puede eliminar el último acudiente activo.");
          require(dao.deleteLink(c, student, guardian) == 1, "Vínculo inexistente.");
          ensurePrincipal(student).execute(c);
          return null;
        });
  }

  public List<Enrollment> enrollments(long student) {
    return transaction(c -> dao.enrollments(c, student));
  }

  public void updateEnrollment(long id, long grade, String status) {
    require(
        status != null && Set.of("Activa", "Completada", "Retirada").contains(status),
        "Estado de matrícula inválido.");
    transaction(
        c -> {
          Enrollment old =
              dao.lockEnrollment(c, id)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Matrícula inexistente."));
          require(
              old.gradeId() == grade || !dao.hasSubscriptions(c, old.studentId(), old.year()),
              "No se cambia el grado de una matrícula con inscripciones.");
          require(dao.activeGrade(c, grade), "Grado inactivo o inexistente.");
          if (status.equals("Activa"))
            require(dao.isActive(c, old.studentId()), "Estudiante inactivo.");
          dao.updateEnrollment(c, new Enrollment(id, old.studentId(), grade, old.year(), status));
          return null;
        });
  }

  public void withdrawEnrollment(long id) {
    transaction(
        c -> {
          Enrollment old =
              dao.lockEnrollment(c, id)
                  .orElseThrow(
                      () ->
                          new com.littlehorizons.erp.util.BusinessException(
                              "Matrícula inexistente."));
          dao.updateEnrollment(
              c, new Enrollment(id, old.studentId(), old.gradeId(), old.year(), "Retirada"));
          return null;
        });
  }

  private Work<Void> ensurePrincipal(long student) {
    return c -> {
      var active = new java.util.ArrayList<GuardianLink>();
      for (var link : dao.links(c, student))
        if (dao.lockGuardian(c, link.guardianId()).orElseThrow().active()) active.add(link);
      require(!active.isEmpty(), "El estudiante necesita un acudiente activo.");
      if (active.stream().noneMatch(GuardianLink::primary)) {
        var next = active.get(0);
        dao.saveLink(
            c,
            new GuardianLink(
                student,
                next.guardianId(),
                next.relationship(),
                true,
                next.emergency(),
                next.financial()));
      }
      return null;
    };
  }
}
