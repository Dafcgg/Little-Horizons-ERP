package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.controller.ActivityAdministrationController;
import com.littlehorizons.erp.controller.ActivityController;
import com.littlehorizons.erp.dao.jdbc.Jdbc;
import com.littlehorizons.erp.dao.jdbc.JdbcAcademicDao;
import com.littlehorizons.erp.dao.jdbc.JdbcActivityDao;
import com.littlehorizons.erp.model.Club;
import com.littlehorizons.erp.model.SchoolEvent;
import com.littlehorizons.erp.model.Teacher;
import com.littlehorizons.erp.service.AcademicService;
import com.littlehorizons.erp.service.ActivityService;
import com.littlehorizons.erp.util.ConnectionProvider;
import com.littlehorizons.erp.view.ConsoleView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ERP_TEST_DB_URL", matches = ".+")
class ActivityDescriptionIntegrationTest {
  private final ConnectionProvider connections =
      () ->
          DriverManager.getConnection(
              System.getenv("ERP_TEST_DB_URL"),
              System.getenv().getOrDefault("ERP_TEST_DB_USER", "root"),
              System.getenv("ERP_TEST_DB_PASSWORD"));
  private final ActivityService service = new ActivityService(connections, new JdbcActivityDao());

  private long teacher() {
    String unique = UUID.randomUUID().toString().substring(0, 12);
    return new AcademicService(connections, new JdbcAcademicDao())
        .registerTeacher(
            new Teacher(0, unique, "Test", "Teacher", unique + "@example.invalid", "Science"));
  }

  @Test
  void clubDescriptionSurvivesCreationQueriesUpdateAndDeactivation() throws Exception {
    String unique = UUID.randomUUID().toString().substring(0, 12);
    long teacher = teacher();
    long id =
        service.createClub(new Club(0, "Club " + unique, "Build educational robots.", teacher, 10));

    assertEquals("Build educational robots.", service.club(id).description());
    assertEquals(
        "Build educational robots.",
        service.clubs().stream()
            .filter(club -> club.id() == id)
            .findFirst()
            .orElseThrow()
            .description());
    try (Connection connection = connections.open()) {
      assertEquals(
          "Build educational robots.",
          Jdbc.query(
                  connection,
                  "SELECT descripcion FROM clubes WHERE id_club=?",
                  row -> row.getString(1),
                  id)
              .get(0));
      assertEquals(
          "Build educational robots.",
          new JdbcActivityDao().lockClub(connection, id).orElseThrow().description());
    }

    service.updateClub(
        new Club(id, "Club " + unique, "Explore sensors and teamwork.", teacher, 12, true));
    service.deactivateClub(id);
    assertEquals("Explore sensors and teamwork.", service.club(id).description());
    assertFalse(service.club(id).active());
  }

  @Test
  void eventDescriptionSurvivesCreationQueriesUpdateAndDeactivation() throws Exception {
    String name = "Event " + UUID.randomUUID().toString().substring(0, 12);
    LocalDate date = LocalDate.now().plusDays(1);
    long id =
        service.createEvent(
            new SchoolEvent(0, name, "Present student experiments.", date, "Científico", false));

    assertEquals("Present student experiments.", service.event(id).description());
    assertEquals(
        "Present student experiments.",
        service.events().stream()
            .filter(event -> event.id() == id)
            .findFirst()
            .orElseThrow()
            .description());
    try (Connection connection = connections.open()) {
      assertEquals(
          "Present student experiments.",
          Jdbc.query(
                  connection,
                  "SELECT descripcion FROM eventos WHERE id_evento=?",
                  row -> row.getString(1),
                  id)
              .get(0));
    }

    service.updateEvent(
        new SchoolEvent(
            id, name, "Share experiment results with families.", date, "Científico", false, true));
    service.deactivateEvent(id);
    assertEquals("Share experiment results with families.", service.event(id).description());
    assertFalse(service.event(id).active());
  }

  @Test
  void allSeedClubsAndEventsHaveDescriptions() throws Exception {
    try (Connection connection = connections.open()) {
      assertEquals(
          30,
          Jdbc.count(
              connection,
              """
              SELECT COUNT(*) FROM clubes
              WHERE id_club BETWEEN 1 AND 30 AND TRIM(descripcion)<>''
              """));
      assertEquals(
          30,
          Jdbc.count(
              connection,
              """
              SELECT COUNT(*) FROM eventos
              WHERE id_evento BETWEEN 1 AND 30 AND TRIM(descripcion)<>''
              """));
    }
  }

  @Test
  void consoleFormsCreateAndEditBothDescriptions() {
    String unique = UUID.randomUUID().toString().substring(0, 12);
    String clubName = "Club " + unique;
    String eventName = "Event " + unique;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    String creationInput =
        String.join(
            "\n",
            "2",
            clubName,
            "Initial club description.",
            String.valueOf(teacher()),
            "10",
            "6",
            eventName,
            "Initial event description.",
            LocalDate.now().toString(),
            "Cultural",
            "0",
            "0",
            "");
    new ActivityController(service, console(creationInput, output)).run();
    Club club =
        service.clubs().stream()
            .filter(value -> value.name().equals(clubName))
            .findFirst()
            .orElseThrow();
    SchoolEvent event =
        service.events().stream()
            .filter(value -> value.name().equals(eventName))
            .findFirst()
            .orElseThrow();
    assertEquals("Initial club description.", club.description());
    assertEquals("Initial event description.", event.description());

    String editingInput =
        String.join(
            "\n",
            "2",
            String.valueOf(club.id()),
            "",
            "Updated club description.",
            "",
            "",
            "",
            "7",
            String.valueOf(event.id()),
            "",
            "Updated event description.",
            "",
            "2",
            "",
            "",
            "0",
            "");
    new ActivityAdministrationController(service, console(editingInput, output)).run();
    assertEquals("Updated club description.", service.club(club.id()).description());
    assertEquals("Updated event description.", service.event(event.id()).description());
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Descripción"));
  }

  private ConsoleView console(String input, ByteArrayOutputStream output) {
    return new ConsoleView(
        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
        new PrintStream(output, true, StandardCharsets.UTF_8));
  }
}
