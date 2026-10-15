package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ConflictException;

class EncounterTests {
  @Test
  void examinationInstructionsAreBoundedAndCannotBeUnnamed() {
    assertDoesNotThrow(
        () ->
            EncounterService.validateExams(
                java.util.List.of(
                    new EncounterDtos.ExamRequest("Blood count", "Bring results to next visit"))));
    assertThrows(
        BusinessRuleException.class,
        () ->
            EncounterService.validateExams(
                java.util.List.of(new EncounterDtos.ExamRequest(" ", "Any"))));
    assertThrows(
        BusinessRuleException.class,
        () ->
            EncounterService.validateExams(
                java.util.Collections.nCopies(101, new EncounterDtos.ExamRequest("Exam", ""))));
  }

  @Test
  void startUsesEstablishmentDateRatherThanUtcDate() {
    Instant scheduled = Instant.parse("2026-10-14T02:00:00Z");
    Instant now = Instant.parse("2026-10-13T18:00:00Z");
    assertDoesNotThrow(
        () -> EncounterService.ensureStartDay(scheduled, now, ZoneId.of("America/Toronto")));
    assertThrows(
        BusinessRuleException.class,
        () -> EncounterService.ensureStartDay(scheduled, now, ZoneOffset.UTC));
  }

  @Test
  void futureDayCannotStartEvenIfCashPaid() {
    assertThrows(
        BusinessRuleException.class,
        () ->
            EncounterService.ensureStartDay(
                Instant.parse("2026-10-14T09:00:00Z"),
                Instant.parse("2026-10-13T20:00:00Z"),
                ZoneOffset.UTC));
  }

  @Test
  void staleReportAndVisitVersionsAreRejected() {
    assertThrows(ConflictException.class, () -> EncounterService.checkVersion(3, 2));
    assertDoesNotThrow(() -> EncounterService.checkVersion(3, 3));
  }
}
