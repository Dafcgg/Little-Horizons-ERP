package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.*;
import java.sql.*;
import java.util.*;

public interface ActivityDao {
  long createClub(Connection c, Club club) throws SQLException;

  List<Club> clubs(Connection c) throws SQLException;

  Optional<Club> lockClub(Connection c, long club) throws SQLException;

  int activeMembers(Connection c, long club) throws SQLException;

  boolean isMember(Connection c, long club, long student) throws SQLException;

  void join(Connection c, long club, long student) throws SQLException;

  int withdraw(Connection c, long club, long student) throws SQLException;

  long createEvent(Connection c, SchoolEvent event) throws SQLException;

  List<SchoolEvent> events(Connection c) throws SQLException;

  Optional<Boolean> lockEvent(Connection c, long event) throws SQLException;

  void participate(Connection c, long event, long student, String position) throws SQLException;

  Optional<Club> club(Connection c, long id, boolean lock) throws SQLException;

  int updateClub(Connection c, Club club) throws SQLException;

  void retireMembers(Connection c, long club) throws SQLException;

  boolean activeTeacher(Connection c, long teacher) throws SQLException;

  boolean activeStudent(Connection c, long student) throws SQLException;

  List<ClubMembership> memberships(Connection c, long club) throws SQLException;

  Optional<ClubMembership> membership(Connection c, long id) throws SQLException;

  int updateMembership(Connection c, long id, java.time.LocalDate joined, String status)
      throws SQLException;

  Optional<SchoolEvent> event(Connection c, long id, boolean lock) throws SQLException;

  int updateEvent(Connection c, SchoolEvent event) throws SQLException;

  boolean hasPositions(Connection c, long event) throws SQLException;

  List<EventParticipation> participants(Connection c, long event) throws SQLException;

  int updateParticipation(Connection c, EventParticipation entry) throws SQLException;

  int deleteParticipation(Connection c, long event, long student) throws SQLException;
}
