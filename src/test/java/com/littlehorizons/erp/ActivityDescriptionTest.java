package com.littlehorizons.erp;

import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.model.Club;
import com.littlehorizons.erp.model.SchoolEvent;
import com.littlehorizons.erp.view.ConsoleView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityDescriptionTest {
  @Test
  void clubDescriptionAppearsInConsoleListings() {
    Club club = new Club(1, "Robotics", "Build and program educational robots.", 2, 10);
    assertEquals("Build and program educational robots.", club.description());
    assertTrue(output(club).contains("Descripción: Build and program educational robots."));
  }

  @Test
  void eventDescriptionAppearsInConsoleListings() {
    SchoolEvent event =
        new SchoolEvent(
            1, "Science fair", "Present student experiments.", LocalDate.now(), "Científico", true);
    assertEquals("Present student experiments.", event.description());
    assertTrue(output(event).contains("Descripción: Present student experiments."));
  }

  private String output(Object activity) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ConsoleView view =
        new ConsoleView(
            new ByteArrayInputStream(new byte[0]),
            new PrintStream(bytes, true, StandardCharsets.UTF_8));
    view.showAll(List.of(activity));
    return bytes.toString(StandardCharsets.UTF_8);
  }
}
