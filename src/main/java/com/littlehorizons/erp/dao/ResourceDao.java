package com.littlehorizons.erp.dao;

import com.littlehorizons.erp.model.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Stores shared resources and loans from the two borrower-specific tables. */
public interface ResourceDao {
  long create(Connection c, SchoolResource resource) throws SQLException;

  List<SchoolResource> resources(Connection c) throws SQLException;

  /** Supplies the common row lock so both types of loan compete for the same resource. */
  Optional<SchoolResource> lock(Connection c, long resource) throws SQLException;

  void setStatus(Connection c, long resource, String status) throws SQLException;

  long createLoan(
      Connection c, long resource, long borrower, BorrowerType type, LocalDate date, LocalDate due)
      throws SQLException;

  /** Selects the table by borrower type because loan IDs can repeat across tables. */
  Optional<Loan> lockLoan(Connection c, long id, BorrowerType type) throws SQLException;

  Optional<Loan> findLoan(Connection c, long id, BorrowerType type) throws SQLException;

  void returnLoan(Connection c, long id, BorrowerType type, LocalDate date) throws SQLException;

  /** Returns open loans due strictly before the supplied date. */
  List<Loan> overdue(Connection c, LocalDate date) throws SQLException;

  int updateResource(Connection c, SchoolResource resource) throws SQLException;

  boolean activeCategory(Connection c, long category) throws SQLException;

  boolean activeBorrower(Connection c, long borrower, BorrowerType type) throws SQLException;

  List<LoanEntry> loans(Connection c, BorrowerType type) throws SQLException;

  Optional<LoanEntry> loanEntry(Connection c, long id, BorrowerType type, boolean lock)
      throws SQLException;

  int updateDueDate(Connection c, long id, BorrowerType type, LocalDate due) throws SQLException;

  int cancelLoan(Connection c, long id, BorrowerType type, LocalDate date, String reason)
      throws SQLException;
}
