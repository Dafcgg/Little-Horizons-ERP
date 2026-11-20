package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.unique;

import com.littlehorizons.erp.dao.jdbc.JdbcFinanceDao;
import com.littlehorizons.erp.dao.jdbc.JdbcStudentDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.AcademicService;
import com.littlehorizons.erp.service.FinanceService;
import com.littlehorizons.erp.service.StudentService;
import com.littlehorizons.erp.util.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

/** Shared setup for the seeded MySQL integration suites. */
abstract class CrudIntegrationFixture {
  final ConnectionProvider connections = MysqlFixture.CONNECTIONS;
  final StudentService students = new StudentService(connections, new JdbcStudentDao());
  @TempDir Path directory;

  @BeforeAll
  static void logging() throws Exception {
    AppLogger.configure();
  }

  BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  FinanceService finance() {
    return new FinanceService(
        connections,
        new JdbcFinanceDao(),
        new TextInvoiceGenerator(directory),
        SchoolInformation.fromEnvironment());
  }

  long guardian() {
    return students.registerGuardian(
        new Guardian(0, unique(), "Test", "Guardian", "3100000000", "Street"));
  }

  long student(long guardian) {
    return students.register(
        new Student(0, unique(), "Test", "Student", LocalDate.of(2015, 1, 1), "Street", "Activo"),
        guardian,
        "Tutor");
  }

  long teacher(AcademicService academics) {
    return academics.registerTeacher(
        new Teacher(0, unique(), "Test", "Teacher", unique() + "@example.invalid", "Math"));
  }

  PaymentRequest request(long obligation, long guardian, String amount) {
    return new PaymentRequest(
        obligation, guardian, money(amount), LocalDate.now(), PaymentMethod.CASH, unique());
  }
}
