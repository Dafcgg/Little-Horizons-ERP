package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CoreRulesTest {
  @Test
  void gradesRespectBoundsAndPrecision() {
    AcademicService.validateGrade(new BigDecimal("0.0"));
    AcademicService.validateGrade(new BigDecimal("5.0"));
    for (String value : List.of("-0.1", "5.1", "3.45"))
      assertThrows(
          BusinessException.class, () -> AcademicService.validateGrade(new BigDecimal(value)));
  }

  @Test
  void missingGradesAreNotZeroAndFinalRoundingIsExplicit() {
    assertTrue(AcademicService.average(List.of()).isEmpty());
    assertEquals(
        new BigDecimal("4.1"),
        AcademicService.average(List.of(new BigDecimal("4.0"), new BigDecimal("4.1")))
            .orElseThrow());
  }

  @Test
  void moneyIsExactAndRejectsExtraDecimals() {
    assertEquals(new BigDecimal("123.40"), FinanceService.money(new BigDecimal("123.4")));
    assertThrows(BusinessException.class, () -> FinanceService.money(new BigDecimal("1.001")));
    assertThrows(BusinessException.class, () -> FinanceService.money(new BigDecimal("-1")));
  }

  @Test
  void simulatedCufeIsDeterministicAndSensitiveToInvoiceIdentity() {
    LocalDate date = LocalDate.of(2026, 1, 1);
    BigDecimal total = new BigDecimal("100.00");
    String cufe = FinanceService.cufe("LH-1", "DEMO", date, total);
    assertEquals(64, cufe.length());
    assertEquals(cufe, FinanceService.cufe("LH-1", "DEMO", date, total));
    assertNotEquals(cufe, FinanceService.cufe("LH-2", "DEMO", date, total));
  }

  @Test
  void invalidNullableServiceInputsFailBeforeOpeningConnections() {
    ConnectionProvider forbidden =
        () -> {
          throw new AssertionError("Connection must not be opened");
        };
    var academics =
        new AcademicService(forbidden, new com.littlehorizons.erp.dao.jdbc.JdbcAcademicDao());
    var activities =
        new ActivityService(forbidden, new com.littlehorizons.erp.dao.jdbc.JdbcActivityDao());
    var resources =
        new ResourceService(forbidden, new com.littlehorizons.erp.dao.jdbc.JdbcResourceDao());
    var finance =
        new FinanceService(
            forbidden,
            new com.littlehorizons.erp.dao.jdbc.JdbcFinanceDao(),
            invoice -> java.nio.file.Path.of("unused"),
            SchoolInformation.fromEnvironment());
    assertThrows(BusinessException.class, () -> academics.recordGrade(null));
    assertThrows(BusinessException.class, () -> academics.courseAverage(1, 2026, null, "A"));
    assertThrows(
        BusinessException.class,
        () ->
            activities.createEvent(
                new com.littlehorizons.erp.model.SchoolEvent(
                    0, "Test", LocalDate.now(), null, false)));
    assertThrows(BusinessException.class, () -> resources.changeStatus(1, null));
    assertThrows(BusinessException.class, () -> finance.confirmPayment(null));
  }

  @Test
  void studentUpdateRejectsNullStatusBeforeOpeningConnection() {
    ConnectionProvider forbidden =
        () -> {
          throw new AssertionError("Connection must not be opened");
        };
    var service =
        new StudentService(forbidden, new com.littlehorizons.erp.dao.jdbc.JdbcStudentDao());
    var student =
        new com.littlehorizons.erp.model.Student(
            1, "123", "Test", "Student", LocalDate.of(2015, 1, 1), "Street", null);
    BusinessException error =
        assertThrows(BusinessException.class, () -> service.updateStudent(student));
    assertEquals("Estado inválido.", error.getMessage());
  }
}
