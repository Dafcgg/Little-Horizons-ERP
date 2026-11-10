package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.*;
import java.sql.*;
import java.util.*;

public interface StudentDao {
  long createStudent(Connection connection, Student student) throws SQLException;

  long createGuardian(Connection connection, Guardian guardian) throws SQLException;

  void linkGuardian(Connection connection, long studentId, long guardianId, String relationship)
      throws SQLException;

  void enroll(Connection connection, long studentId, long gradeId, int year) throws SQLException;

  boolean isActive(Connection connection, long studentId) throws SQLException;

  boolean hasGuardian(Connection connection, long studentId) throws SQLException;

  List<Student> findStudents(Connection connection) throws SQLException;

  List<Guardian> findGuardians(Connection connection) throws SQLException;

  Optional<Student> lockStudent(Connection c, long id) throws SQLException;

  Optional<Guardian> lockGuardian(Connection c, long id) throws SQLException;

  int updateStudent(Connection c, Student student) throws SQLException;

  int updateGuardian(Connection c, Guardian guardian) throws SQLException;

  List<GuardianLink> links(Connection c, long student) throws SQLException;

  void saveLink(Connection c, GuardianLink link) throws SQLException;

  int deleteLink(Connection c, long student, long guardian) throws SQLException;

  int activeGuardians(Connection c, long student, long excluded) throws SQLException;

  List<Long> guardianStudents(Connection c, long guardian) throws SQLException;

  List<Enrollment> enrollments(Connection c, long student) throws SQLException;

  Optional<Enrollment> lockEnrollment(Connection c, long id) throws SQLException;

  boolean hasSubscriptions(Connection c, long student, int year) throws SQLException;

  int updateEnrollment(Connection c, Enrollment enrollment) throws SQLException;

  boolean activeGrade(Connection c, long grade) throws SQLException;
}
