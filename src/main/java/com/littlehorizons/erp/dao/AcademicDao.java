package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

public interface AcademicDao {
  long createTeacher(Connection c, Teacher teacher) throws SQLException;

  long createSubject(Connection c, Subject subject) throws SQLException;

  List<Teacher> teachers(Connection c) throws SQLException;

  List<Subject> subjects(Connection c) throws SQLException;

  Optional<Long> lockGrade(Connection c, long subject) throws SQLException;

  boolean lockTeacher(Connection c, long teacher) throws SQLException;

  boolean hasScheduleConflict(
      Connection c,
      long teacher,
      long grade,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end)
      throws SQLException;

  boolean canSubscribe(Connection c, long student, long subject, int year, String section)
      throws SQLException;

  void assign(
      Connection c,
      long teacher,
      long subject,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end)
      throws SQLException;

  void subscribe(
      Connection c, long student, long subject, int year, AcademicPeriod period, String section)
      throws SQLException;

  boolean canGrade(
      Connection c, long student, long subject, long teacher, int year, AcademicPeriod period)
      throws SQLException;

  void saveGrade(Connection c, AcademicRecord record) throws SQLException;

  void attendance(
      Connection c,
      long student,
      long subject,
      long teacher,
      int year,
      AcademicPeriod period,
      LocalDate date,
      AttendanceStatus status)
      throws SQLException;

  List<AcademicRecord> history(Connection c, long student) throws SQLException;

  List<String> absenceReport(Connection c, LocalDate start, LocalDate end, int threshold)
      throws SQLException;

  BigDecimal courseAverage(
      Connection c, long subject, int year, AcademicPeriod period, String section)
      throws SQLException;

  Optional<Teacher> teacher(Connection c, long id, boolean lock) throws SQLException;

  Optional<Subject> subject(Connection c, long id, boolean lock) throws SQLException;

  int updateTeacher(Connection c, Teacher teacher) throws SQLException;

  int updateSubject(Connection c, Subject subject) throws SQLException;

  boolean subjectHasHistory(Connection c, long subject) throws SQLException;

  List<TeachingAssignment> assignments(Connection c) throws SQLException;

  Optional<TeachingAssignment> assignment(Connection c, long id, boolean lock) throws SQLException;

  int updateAssignment(Connection c, TeachingAssignment assignment) throws SQLException;

  boolean assignmentHasHistory(Connection c, TeachingAssignment assignment) throws SQLException;

  List<ClassSchedule> schedules(Connection c) throws SQLException;

  Optional<ClassSchedule> schedule(Connection c, long id) throws SQLException;

  int updateSchedule(Connection c, ClassSchedule schedule) throws SQLException;

  int deleteSchedule(Connection c, long id) throws SQLException;

  boolean scheduleConflict(
      Connection c,
      long teacher,
      long grade,
      int year,
      String section,
      int day,
      LocalTime start,
      LocalTime end,
      long excluded)
      throws SQLException;

  List<Subscription> subscriptions(Connection c, long student) throws SQLException;

  Optional<Subscription> subscription(Connection c, long id) throws SQLException;

  int updateSubscription(Connection c, Subscription subscription) throws SQLException;

  boolean subscriptionHasHistory(Connection c, Subscription subscription) throws SQLException;

  List<AttendanceRecord> attendances(Connection c, long student) throws SQLException;

  int deleteAttendance(Connection c, long id) throws SQLException;

  int deleteGrade(Connection c, long student, long subject, int year, AcademicPeriod period)
      throws SQLException;

  boolean activeGrade(Connection c, long grade) throws SQLException;

  boolean lockStudent(Connection c, long student) throws SQLException;
}
