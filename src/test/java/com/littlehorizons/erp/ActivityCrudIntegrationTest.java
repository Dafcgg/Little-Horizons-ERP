package com.littlehorizons.erp;

import static com.littlehorizons.erp.MysqlFixture.unique;
import static org.junit.jupiter.api.Assertions.*;

import com.littlehorizons.erp.dao.jdbc.JdbcAcademicDao;
import com.littlehorizons.erp.dao.jdbc.JdbcActivityDao;
import com.littlehorizons.erp.model.*;
import com.littlehorizons.erp.service.AcademicService;
import com.littlehorizons.erp.service.ActivityService;
import com.littlehorizons.erp.util.BusinessException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = MysqlFixture.URL, matches = ".+")
class ActivityCrudIntegrationTest extends CrudIntegrationFixture {
  private final AcademicService academics = new AcademicService(connections, new JdbcAcademicDao());
  private final ActivityService activities = new ActivityService(connections, new JdbcActivityDao());

  @Test
  void clubMembershipCrudGuardsCapacityAndRetiresMembersOnInactivation() {
    long teacher = teacher(academics), first = student(guardian()), second = student(guardian());
    long club = activities.createClub(new Club(0, "Test " + unique(), teacher, 1));
    activities.join(club, first);
    assertThrows(
        BusinessException.class,
        () -> activities.updateClub(new Club(club, "Invalid", teacher, 0, true)));
    ClubMembership membership = activities.memberships(club).get(0);
    activities.updateMembership(membership.id(), LocalDate.now().minusDays(1), "Retirada");
    activities.join(club, second);
    assertThrows(
        BusinessException.class,
        () -> activities.updateMembership(membership.id(), LocalDate.now(), "Activa"));
    activities.deactivateClub(club);
    assertFalse(activities.club(club).active());
    assertTrue(activities.memberships(club).stream().allMatch(m -> m.status().equals("Retirada")));
    activities.updateClub(new Club(club, "Edited " + unique(), teacher, 2, true));
    assertEquals(2, activities.club(club).capacity());
    activities.updateMembership(membership.id(), LocalDate.now(), "Activa");
    assertEquals(
        1, activities.memberships(club).stream().filter(m -> m.status().equals("Activa")).count());
  }

  @Test
  void eventAndParticipantCrudKeepsResultsConsistent() {
    long student = student(guardian());
    long event =
        activities.createEvent(
            new SchoolEvent(0, "Test " + unique(), LocalDate.now(), "Cultural", true));
    activities.participate(event, student, "Participante");
    activities.updateParticipation(new EventParticipation(event, student, "1er Lugar"));
    assertEquals("1er Lugar", activities.participants(event).get(0).position());
    assertThrows(
        BusinessException.class,
        () ->
            activities.updateEvent(
                new SchoolEvent(event, "Updated", LocalDate.now(), "Cultural", false, true)));
    activities.updateParticipation(new EventParticipation(event, student, ""));
    activities.updateEvent(
        new SchoolEvent(event, "Updated", LocalDate.now(), "Social", false, true));
    assertEquals("Updated", activities.event(event).name());
    activities.removeParticipant(event, student);
    assertTrue(activities.participants(event).isEmpty());
    activities.deactivateEvent(event);
    assertFalse(activities.event(event).active());
    assertThrows(BusinessException.class, () -> activities.participate(event, student, ""));
  }

  @Test
  void concurrentRestorationCannotExceedClubCapacity() throws Exception {
    long first = student(guardian()), second = student(guardian());
    long club = activities.createClub(new Club(0, "Test " + unique(), teacher(academics), 1));
    activities.join(club, first);
    long membership = activities.memberships(club).get(0).id();
    activities.withdraw(club, first);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (Runnable task :
          List.<Runnable>of(
              () -> activities.updateMembership(membership, LocalDate.now(), "Activa"),
              () -> activities.join(club, second)))
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    task.run();
                    return true;
                  } catch (BusinessException rejected) {
                    assertEquals("El club no tiene cupos.", rejected.getMessage());
                    return false;
                  }
                }));
      start.countDown();
      int successes = 0;
      for (var future : futures) if (future.get(20, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
      assertEquals(
          1,
          activities.memberships(club).stream().filter(m -> m.status().equals("Activa")).count());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void reducingCapacityAndJoiningCannotProduceAnOverbookedClub() throws Exception {
    long first = student(guardian()), second = student(guardian()), teacher = teacher(academics);
    long club = activities.createClub(new Club(0, "Test " + unique(), teacher, 2));
    activities.join(club, first);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      futures.add(
          executor.submit(
              () -> {
                try {
                  start.await();
                  activities.join(club, second);
                } catch (BusinessException expected) {
                  assertEquals("El club no tiene cupos.", expected.getMessage());
                } catch (InterruptedException e) {
                  throw new AssertionError(e);
                }
              }));
      futures.add(
          executor.submit(
              () -> {
                try {
                  start.await();
                  activities.updateClub(new Club(club, "Edited " + unique(), teacher, 1, true));
                } catch (BusinessException expected) {
                  assertEquals(
                      "El cupo no puede ser menor que la membresía activa.", expected.getMessage());
                } catch (InterruptedException e) {
                  throw new AssertionError(e);
                }
              }));
      start.countDown();
      for (var future : futures) future.get(20, TimeUnit.SECONDS);
      assertTrue(
          activities.memberships(club).stream().filter(m -> m.status().equals("Activa")).count()
              <= activities.club(club).capacity());
    } finally {
      executor.shutdownNow();
    }
  }
}
