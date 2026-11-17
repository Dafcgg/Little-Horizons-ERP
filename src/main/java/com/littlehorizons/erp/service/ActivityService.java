package com.littlehorizons.erp.service;

import com.littlehorizons.erp.dao.ActivityDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.util.*;
import java.util.*;

public final class ActivityService extends TransactionalService {
  private final ActivityDao dao;

  public ActivityService(ConnectionProvider connections, ActivityDao dao) {
    super(connections);
    this.dao = dao;
  }

  private static void validateDescription(String description) {
    textByteLength(description, "La descripción", 65_535);
  }

  public long createClub(Club club) {
    require(club != null, "Datos obligatorios.");
    text(club.name(), "Nombre", 100);
    validateDescription(club.description());
    require(club.capacity() > 0, "El cupo debe ser positivo.");
    return transaction(
        c -> {
          require(dao.activeTeacher(c, club.teacherId()), "Profesor no disponible.");
          return dao.createClub(c, club);
        });
  }

  public List<Club> clubs() {
    return transaction(dao::clubs);
  }

  public void join(long clubId, long student) {
    transaction(
        c -> {
          Club club =
              dao.lockClub(c, clubId)
                  .orElseThrow(() -> new BusinessException("Club no disponible."));
          require(!dao.isMember(c, clubId, student), "El estudiante ya pertenece al club.");
          require(dao.activeMembers(c, clubId) < club.capacity(), "El club no tiene cupos.");
          require(dao.activeStudent(c, student), "Estudiante inactivo o inexistente.");
          dao.join(c, clubId, student);
          return null;
        });
  }

  public void withdraw(long club, long student) {
    transaction(
        c -> {
          dao.lockClub(c, club).orElseThrow(() -> new BusinessException("Club inexistente."));
          require(dao.withdraw(c, club, student) == 1, "Membresía activa inexistente.");
          return null;
        });
  }

  public long createEvent(SchoolEvent event) {
    require(event != null, "Datos obligatorios.");
    text(event.name(), "Nombre", 150);
    validateDescription(event.description());
    require(isDatabaseDate(event.date()), "Fecha obligatoria.");
    require(
        event.type() != null
            && Set.of("Académico", "Cultural", "Deportivo", "Social", "Científico")
                .contains(event.type()),
        "Tipo de evento inválido.");
    return transaction(c -> dao.createEvent(c, event));
  }

  public List<SchoolEvent> events() {
    return transaction(dao::events);
  }

  private static String normalizePosition(String position) {
    if (position == null || position.isBlank()) return null;
    require(
        Set.of("1er Lugar", "2do Lugar", "3er Lugar", "Participante").contains(position),
        "Posición inválida.");
    return position;
  }

  private void requireAvailablePosition(
      java.sql.Connection connection, long event, long student, String position)
      throws java.sql.SQLException {
    if (position != null && !position.equals("Participante")) {
      require(
          !dao.hasPositionOccupied(connection, event, position, student),
          "La posición ya ha sido asignada a otro participante.");
    }
  }

  public void participate(long event, long student, String position) {
    String result = normalizePosition(position);
    transaction(
        c -> {
          boolean competitive =
              dao.lockEvent(c, event)
                  .orElseThrow(() -> new BusinessException("Evento inexistente."));
          require(competitive || result == null, "El evento no tiene competencia.");
          requireAvailablePosition(c, event, student, result);
          require(dao.activeStudent(c, student), "Estudiante inactivo o inexistente.");
          dao.participate(c, event, student, result);
          return null;
        });
  }

  public Club club(long id) {
    return transaction(
        c -> dao.club(c, id, false).orElseThrow(() -> new BusinessException("Club inexistente.")));
  }

  public void updateClub(Club club) {
    require(club != null, "Club obligatorio.");
    text(club.name(), "Nombre", 100);
    validateDescription(club.description());
    require(club.capacity() > 0, "Cupo inválido.");
    transaction(
        c -> {
          dao.club(c, club.id(), true)
              .orElseThrow(() -> new BusinessException("Club inexistente."));
          require(
              club.capacity() >= dao.activeMembers(c, club.id()),
              "El cupo no puede ser menor que la membresía activa.");
          if (club.active())
            require(dao.activeTeacher(c, club.teacherId()), "Profesor no disponible.");
          if (!club.active()) dao.retireMembers(c, club.id());
          dao.updateClub(c, club);
          return null;
        });
  }

  public void deactivateClub(long id) {
    transaction(
        c -> {
          Club club =
              dao.club(c, id, true).orElseThrow(() -> new BusinessException("Club inexistente."));
          dao.retireMembers(c, id);
          dao.updateClub(c, new Club(id, club.name(), club.teacherId(), club.capacity(), false));
          return null;
        });
  }

  public List<ClubMembership> memberships(long club) {
    return transaction(c -> dao.memberships(c, club));
  }

  public void updateMembership(long id, java.time.LocalDate joined, String status) {
    require(
        isDatabaseDate(joined) && !joined.isAfter(java.time.LocalDate.now()), "Fecha inválida.");
    require(status != null && Set.of("Activa", "Retirada").contains(status), "Estado inválido.");
    transaction(
        c -> {
          ClubMembership initial =
              dao.membership(c, id)
                  .orElseThrow(() -> new BusinessException("Membresía inexistente."));
          Club club = dao.club(c, initial.clubId(), true).orElseThrow();
          ClubMembership old = dao.membership(c, id).orElseThrow();
          if (status.equals("Activa")) {
            require(
                club.active() && dao.activeStudent(c, old.studentId()),
                "Club o estudiante inactivo.");
            if (!old.status().equals("Activa")) {
              require(
                  !dao.isMember(c, old.clubId(), old.studentId()),
                  "Ya existe una membresía activa.");
              require(
                  dao.activeMembers(c, old.clubId()) < club.capacity(), "El club no tiene cupos.");
            }
          }
          dao.updateMembership(c, id, joined, status);
          return null;
        });
  }

  public SchoolEvent event(long id) {
    return transaction(
        c ->
            dao.event(c, id, false)
                .orElseThrow(() -> new BusinessException("Evento inexistente.")));
  }

  public void updateEvent(SchoolEvent event) {
    require(event != null && isDatabaseDate(event.date()), "Datos del evento obligatorios.");
    text(event.name(), "Nombre", 150);
    validateDescription(event.description());
    require(
        event.type() != null
            && Set.of("Académico", "Cultural", "Deportivo", "Social", "Científico")
                .contains(event.type()),
        "Tipo inválido.");
    transaction(
        c -> {
          dao.event(c, event.id(), true)
              .orElseThrow(() -> new BusinessException("Evento inexistente."));
          require(
              event.competitive() || !dao.hasPositions(c, event.id()),
              "Elimine las posiciones antes de retirar la competencia.");
          dao.updateEvent(c, event);
          return null;
        });
  }

  public void deactivateEvent(long id) {
    transaction(
        c -> {
          SchoolEvent e =
              dao.event(c, id, true)
                  .orElseThrow(() -> new BusinessException("Evento inexistente."));
          dao.updateEvent(
              c, new SchoolEvent(id, e.name(), e.date(), e.type(), e.competitive(), false));
          return null;
        });
  }

  public List<EventParticipation> participants(long event) {
    return transaction(c -> dao.participants(c, event));
  }

  public void updateParticipation(EventParticipation entry) {
    require(entry != null, "Participación obligatoria.");
    String result = normalizePosition(entry.position());
    transaction(
        c -> {
          SchoolEvent event =
              dao.event(c, entry.eventId(), true)
                  .orElseThrow(() -> new BusinessException("Evento inexistente."));
          require(event.competitive() || result == null, "El evento no tiene competencia.");
          requireAvailablePosition(c, entry.eventId(), entry.studentId(), result);
          var updated = new EventParticipation(entry.eventId(), entry.studentId(), result);
          require(dao.updateParticipation(c, updated) == 1, "Participante inexistente.");
          return null;
        });
  }

  public void removeParticipant(long event, long student) {
    transaction(
        c -> {
          dao.event(c, event, true).orElseThrow(() -> new BusinessException("Evento inexistente."));
          require(dao.deleteParticipation(c, event, student) == 1, "Participante inexistente.");
          return null;
        });
  }
}
