package com.littlehorizons.erp.dao.jdbc;

import com.littlehorizons.erp.dao.ActivityDao;
import com.littlehorizons.erp.model.*;
import java.sql.*;
import java.util.*;

public final class JdbcActivityDao implements ActivityDao {
  public long createClub(Connection c, Club club) throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO clubes(nombre_club,id_profesor_supervisor,capacidad_maxima) VALUES(?,?,?)",
        club.name(),
        club.teacherId(),
        club.capacity());
  }

  public List<Club> clubs(Connection c) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM clubes ORDER BY id_club", this::club);
  }

  private Club club(ResultSet r) throws SQLException {
    return new Club(
        r.getLong("id_club"),
        r.getString("nombre_club"),
        r.getLong("id_profesor_supervisor"),
        r.getInt("capacidad_maxima"),
        r.getBoolean("esta_activo"));
  }

  public Optional<Club> lockClub(Connection c, long id) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM clubes WHERE id_club=? AND esta_activo=TRUE FOR UPDATE",
            this::club,
            id)
        .stream()
        .findFirst();
  }

  public int activeMembers(Connection c, long id) throws SQLException {
    return (int)
        Jdbc.count(
            c,
            "SELECT COUNT(*) FROM membresias_club WHERE id_club=? AND estado_membresia='Activa'",
            id);
  }

  public boolean isMember(Connection c, long club, long student) throws SQLException {
    return Jdbc.count(
            c,
            "SELECT COUNT(*) FROM membresias_club WHERE id_club=? AND id_estudiante=? AND"
                + " estado_membresia='Activa'",
            club,
            student)
        > 0;
  }

  public void join(Connection c, long club, long student) throws SQLException {
    Jdbc.update(
        c,
        "INSERT INTO membresias_club(id_estudiante,id_club,fecha_ingreso) VALUES(?,?,CURRENT_DATE)",
        student,
        club);
  }

  public int withdraw(Connection c, long club, long student) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE membresias_club SET estado_membresia='Retirada',fecha_retiro=CURRENT_DATE WHERE"
            + " id_club=? AND id_estudiante=? AND estado_membresia='Activa'",
        club,
        student);
  }

  public long createEvent(Connection c, SchoolEvent event) throws SQLException {
    return Jdbc.insert(
        c,
        "INSERT INTO eventos(nombre_evento,fecha_evento,tipo_evento,tiene_competencia)"
            + " VALUES(?,?,?,?)",
        event.name(),
        event.date(),
        event.type(),
        event.competitive());
  }

  public List<SchoolEvent> events(Connection c) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM eventos ORDER BY fecha_evento",
        r ->
            new SchoolEvent(
                r.getLong("id_evento"),
                r.getString("nombre_evento"),
                r.getDate("fecha_evento").toLocalDate(),
                r.getString("tipo_evento"),
                r.getBoolean("tiene_competencia"),
                r.getBoolean("esta_activo")));
  }

  public Optional<Boolean> lockEvent(Connection c, long event) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT tiene_competencia FROM eventos WHERE id_evento=? AND esta_activo=TRUE FOR"
                + " UPDATE",
            r -> r.getBoolean(1),
            event)
        .stream()
        .findFirst();
  }

  public void participate(Connection c, long event, long student, String position)
      throws SQLException {
    Jdbc.update(
        c,
        "INSERT INTO participantes_evento(id_evento,id_estudiante,posicion_obtenida) VALUES(?,?,?)",
        event,
        student,
        position);
  }

  public Optional<Club> club(Connection c, long id, boolean lock) throws SQLException {
    return Jdbc.query(
            c, "SELECT * FROM clubes WHERE id_club=?" + (lock ? " FOR UPDATE" : ""), this::club, id)
        .stream()
        .findFirst();
  }

  public int updateClub(Connection c, Club v) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE clubes SET nombre_club=?,id_profesor_supervisor=?,capacidad_maxima=?,esta_activo=?"
            + " WHERE id_club=?",
        v.name(),
        v.teacherId(),
        v.capacity(),
        v.active(),
        v.id());
  }

  public void retireMembers(Connection c, long club) throws SQLException {
    Jdbc.update(
        c,
        "UPDATE membresias_club SET estado_membresia='Retirada',fecha_retiro=CURRENT_DATE WHERE"
            + " id_club=? AND estado_membresia='Activa'",
        club);
  }

  public boolean activeTeacher(Connection c, long teacher) throws SQLException {
    return !Jdbc.query(
            c,
            "SELECT id_profesor FROM profesores WHERE id_profesor=? AND estado='Activo' FOR UPDATE",
            r -> r.getLong(1),
            teacher)
        .isEmpty();
  }

  public boolean activeStudent(Connection c, long student) throws SQLException {
    return !Jdbc.query(
            c,
            "SELECT id_estudiante FROM estudiantes WHERE id_estudiante=? AND estado='Activo' FOR"
                + " UPDATE",
            r -> r.getLong(1),
            student)
        .isEmpty();
  }

  private ClubMembership membership(java.sql.ResultSet r) throws SQLException {
    return new ClubMembership(
        r.getLong("id_membresia"),
        r.getLong("id_estudiante"),
        r.getLong("id_club"),
        r.getDate("fecha_ingreso").toLocalDate(),
        r.getString("estado_membresia"));
  }

  public List<ClubMembership> memberships(Connection c, long club) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT * FROM membresias_club WHERE id_club=? ORDER BY id_membresia",
        this::membership,
        club);
  }

  public Optional<ClubMembership> membership(Connection c, long id) throws SQLException {
    return Jdbc.query(c, "SELECT * FROM membresias_club WHERE id_membresia=?", this::membership, id)
        .stream()
        .findFirst();
  }

  public int updateMembership(Connection c, long id, java.time.LocalDate joined, String status)
      throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE membresias_club SET fecha_ingreso=?,estado_membresia=?,fecha_retiro=CASE WHEN"
            + " ?='Retirada' THEN CURRENT_DATE ELSE NULL END WHERE id_membresia=?",
        joined,
        status,
        status,
        id);
  }

  public Optional<SchoolEvent> event(Connection c, long id, boolean lock) throws SQLException {
    return Jdbc.query(
            c,
            "SELECT * FROM eventos WHERE id_evento=?" + (lock ? " FOR UPDATE" : ""),
            r ->
                new SchoolEvent(
                    r.getLong("id_evento"),
                    r.getString("nombre_evento"),
                    r.getDate("fecha_evento").toLocalDate(),
                    r.getString("tipo_evento"),
                    r.getBoolean("tiene_competencia"),
                    r.getBoolean("esta_activo")),
            id)
        .stream()
        .findFirst();
  }

  public int updateEvent(Connection c, SchoolEvent v) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE eventos SET"
            + " nombre_evento=?,fecha_evento=?,tipo_evento=?,tiene_competencia=?,esta_activo=?"
            + " WHERE id_evento=?",
        v.name(),
        v.date(),
        v.type(),
        v.competitive(),
        v.active(),
        v.id());
  }

  public boolean hasPositions(Connection c, long event) throws SQLException {
    return Jdbc.count(
            c,
            "SELECT COUNT(*) FROM participantes_evento WHERE id_evento=? AND posicion_obtenida IS"
                + " NOT NULL AND posicion_obtenida<>''",
            event)
        > 0;
  }

  public List<EventParticipation> participants(Connection c, long event) throws SQLException {
    return Jdbc.query(
        c,
        "SELECT id_evento,id_estudiante,posicion_obtenida FROM participantes_evento WHERE"
            + " id_evento=? ORDER BY id_estudiante",
        r -> new EventParticipation(r.getLong(1), r.getLong(2), r.getString(3)),
        event);
  }

  public int updateParticipation(Connection c, EventParticipation v) throws SQLException {
    return Jdbc.update(
        c,
        "UPDATE participantes_evento SET posicion_obtenida=? WHERE id_evento=? AND id_estudiante=?",
        v.position(),
        v.eventId(),
        v.studentId());
  }

  public int deleteParticipation(Connection c, long event, long student) throws SQLException {
    return Jdbc.update(
        c,
        "DELETE FROM participantes_evento WHERE id_evento=? AND id_estudiante=?",
        event,
        student);
  }
}
