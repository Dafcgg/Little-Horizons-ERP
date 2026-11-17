package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.CONNECTIONS;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.*;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.*;
import com.littlehorizons.erp.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class BoundaryInputsIntegrationTest {
  private static final LocalDate LEAP_DAY = LocalDate.of(2012, 2, 29);
  private final StudentService students = new StudentService(CONNECTIONS, new JdbcStudentDao());
  private final AcademicService academics = new AcademicService(CONNECTIONS, new JdbcAcademicDao());
  private final ActivityService activities =
      new ActivityService(CONNECTIONS, new JdbcActivityDao());
  private final ResourceService resources = new ResourceService(CONNECTIONS, new JdbcResourceDao());
  private final CatalogService catalogs = new CatalogService(CONNECTIONS, new JdbcCatalogDao());
  private final FinanceService finance =
      new FinanceService(
          CONNECTIONS,
          new JdbcFinanceDao(),
          invoice -> {
            throw new AssertionError("This case should not render an invoice");
          },
          SchoolInformation.fromEnvironment());

  enum Field {
    STUDENT_DOCUMENT(20, true),
    STUDENT_NAME(100, true),
    STUDENT_ADDRESS(255, false),
    GUARDIAN_PHONE(20, true),
    GUARDIAN_ADDRESS(255, false),
    TEACHER_NAME(100, true),
    SUBJECT_NAME(100, true),
    CLUB_NAME(100, true),
    EVENT_NAME(150, true),
    CONCEPT_NAME(100, true),
    RESOURCE_LOCATION(100, true),
    CATEGORY_NAME(50, true);

    final int limit;
    final boolean required;

    Field(int limit, boolean required) {
      this.limit = limit;
      this.required = required;
    }
  }

  record Sample(Field field, int index, String value, boolean accepted) {
    @Override
    public String toString() {
      return field + " #" + index;
    }
  }

  static Stream<Sample> inputs() {
    return Stream.of(Field.values())
        .flatMap(field -> IntStream.range(0, 136).mapToObj(index -> sample(field, index)));
  }

  private static Sample sample(Field field, int index) {
    var random = new Random(590_221L + field.ordinal() * 136 + index);
    // Give distinct values to unique columns even under a Unicode-insensitive collation.
    String id = "QA" + field.ordinal() + "x" + Integer.toUnsignedString(random.nextInt(), 36);
    String prefix = id + "'\"\\;()";
    int variant = index < 2 ? index : 2 + (index - 2) % 8;
    String value =
        switch (variant) {
          case 0 -> null;
          case 1 -> "";
          case 2 -> prefix + (field.limit >= prefix.length() + 3 ? "🚀\r\n" : "");
          case 3 -> id + "🚀".repeat(field.limit - id.length());
          case 4 -> " ".repeat(index + 1);
          case 5 -> id + "é".repeat(field.limit - 1 - id.length());
          case 6 -> id + "ñ".repeat(field.limit + 1 - id.length());
          case 7 -> id + "漢".repeat(500 + random.nextInt(4501) - id.length());
          case 8 -> id + (field.limit > 20 ? "\u0000é🔥" : "é🔥");
          default -> id + "雪Δ";
        };
    boolean accepted =
        value == null
            ? !field.required
            : value.codePointCount(0, value.length()) <= field.limit
                && (!field.required || !value.isBlank());
    return new Sample(field, index, value, accepted);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("inputs")
  void textMatchesMysqlColumnLimitsAndRoundTripsLiterally(Sample sample) {
    if (!sample.accepted) {
      BusinessException rejection =
          assertThrows(BusinessException.class, () -> writeAndRead(sample.field, sample.value));
      assertNull(rejection.getCause(), "Expected field validation before a JDBC error");
      return;
    }
    assertEquals(sample.value, writeAndRead(sample.field, sample.value));
  }

  private String writeAndRead(Field field, String value) {
    String key = MysqlFixture.unique();
    return switch (field) {
      case STUDENT_DOCUMENT, STUDENT_NAME, STUDENT_ADDRESS -> {
        long id =
            students.register(
                new Student(
                    0,
                    field == Field.STUDENT_DOCUMENT ? value : key,
                    field == Field.STUDENT_NAME ? value : "Student",
                    "Boundary",
                    LEAP_DAY,
                    field == Field.STUDENT_ADDRESS ? value : "Address",
                    "Activo"),
                1,
                "Tutor");
        Student saved = students.student(id);
        yield switch (field) {
          case STUDENT_DOCUMENT -> saved.document();
          case STUDENT_NAME -> saved.firstName();
          default -> saved.address();
        };
      }
      case GUARDIAN_PHONE, GUARDIAN_ADDRESS -> {
        long id =
            students.registerGuardian(
                new Guardian(
                    0,
                    key,
                    "Test",
                    "Guardian",
                    field == Field.GUARDIAN_PHONE ? value : "555",
                    field == Field.GUARDIAN_ADDRESS ? value : null));
        Guardian saved = students.guardian(id);
        yield field == Field.GUARDIAN_PHONE ? saved.phone() : saved.address();
      }
      case TEACHER_NAME ->
          academics
              .teacher(
                  academics.registerTeacher(
                      new Teacher(0, key, value, "Teacher", key + "@example.invalid", null)))
              .firstName();
      case SUBJECT_NAME ->
          academics.subject(academics.registerSubject(new Subject(0, key, value, 1))).name();
      case CLUB_NAME -> activities.club(activities.createClub(new Club(0, value, 1, 2))).name();
      case EVENT_NAME ->
          activities
              .event(activities.createEvent(new SchoolEvent(0, value, LEAP_DAY, "Cultural", true)))
              .name();
      case CONCEPT_NAME ->
          finance
              .concept(finance.createConcept(key, value, BigDecimal.ZERO, BigDecimal.ZERO))
              .name();
      case RESOURCE_LOCATION ->
          resources
              .resource(
                  resources.create(new SchoolResource(0, key, "Equipment", 1, value, "Disponible")))
              .location();
      case CATEGORY_NAME ->
          catalogs
              .get(
                  CatalogType.RESOURCE_CATEGORY,
                  catalogs.create(
                      CatalogType.RESOURCE_CATEGORY, new CatalogEntry(0, value, 0, true)))
              .name();
    };
  }

  @ParameterizedTest
  @ValueSource(strings = {"1000-02-28", "1500-03-01", "1582-10-10", "1600-02-29", "2000-02-29"})
  void dateAloneRoundTripsThroughJdbcWithoutCalendarConversion(String isoDate) {
    LocalDate date = LocalDate.parse(isoDate);
    long id =
        activities.createEvent(new SchoolEvent(0, MysqlFixture.unique(), date, "Cultural", false));
    assertEquals(date, activities.event(id).date());
    long student =
        students.register(
            new Student(0, MysqlFixture.unique(), "Student", "Boundary", date, null, "Activo"),
            1,
            "Tutor");
    assertEquals(date, students.student(student).birthDate());
  }

  @Test
  void rejectsDatesOutsideMysqlRangeAndBirthDatesInTheFuture() {
    for (LocalDate date :
        new LocalDate[] {LocalDate.of(999, 12, 31), LocalDate.now().plusDays(1)}) {
      BusinessException rejection =
          assertThrows(
              BusinessException.class,
              () ->
                  students.register(
                      new Student(
                          0, MysqlFixture.unique(), "Student", "Boundary", date, null, "Activo"),
                      1,
                      "Tutor"));
      assertNull(rejection.getCause());
    }
    BusinessException rejection =
        assertThrows(
            BusinessException.class,
            () ->
                activities.createEvent(
                    new SchoolEvent(
                        0, MysqlFixture.unique(), LocalDate.of(10000, 1, 1), "Cultural", false)));
    assertNull(rejection.getCause());
  }

  @ParameterizedTest
  @CsvSource({
    "0.0,true",
    "5.0,true",
    "-0.01,false",
    "5.01,false",
    "3.456,false",
    "1.200,true",
    "0.00000,true",
    "1E+2,false"
  })
  void gradeRangeAndSignificantScale(String input, boolean accepted) {
    Runnable validate = () -> AcademicService.validateGrade(new BigDecimal(input));
    if (accepted) assertDoesNotThrow(validate::run);
    else assertThrows(BusinessException.class, validate::run);
  }

  @ParameterizedTest
  @CsvSource({
    "-0.01,false",
    "0.00,true",
    "1.00000,true",
    "0.00001,false",
    "99999999.99,true",
    "100000000.00,false",
    "999999999999.99,false",
    "1E-2147483647,false"
  })
  void moneyUsesFixedScaleWithinDatabaseRange(String input, boolean accepted) {
    BigDecimal amount = new BigDecimal(input);
    if (accepted) assertEquals(amount.setScale(2), FinanceService.money(amount));
    else assertThrows(BusinessException.class, () -> FinanceService.money(amount));
  }
}
