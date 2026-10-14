package org.novasos.healthysv2.care;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.*;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;

class EncounterStartTests {
  private final UUID org = UUID.randomUUID(),
      person = UUID.randomUUID(),
      doctor = UUID.randomUUID();
  private final CareVisitRepository visits = mock(CareVisitRepository.class);
  private final CurrentUserContext users = mock(CurrentUserContext.class);
  private final LicensingAccess licensing = mock(LicensingAccess.class);
  private final PatientAccessService access = mock(PatientAccessService.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final CarePolicy policy = new CarePolicy(Duration.ofDays(7), 30);
  private final EncounterService service =
      new EncounterService(
          visits,
          users,
          licensing,
          access,
          jdbc,
          mock(AuditTrail.class),
          policy,
          Duration.ofHours(4));

  private final org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess
      permissions =
          mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class);

  @org.junit.jupiter.api.BeforeEach
  void configurePermissions() {
    service.configurePermissions(permissions);
  }

  private CareVisit visit() {
    Instant yesterday = Instant.now().minus(Duration.ofDays(1));
    var episode = CareEpisode.create(UUID.randomUUID(), org, "Fever", policy, yesterday);
    var visit = CareVisit.prepare(episode, doctor, yesterday.plusSeconds(1), policy, yesterday);
    episode.confirmPayment();
    visit.releaseAfterPayment();
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(person, org, Set.of("DOCTOR")));
    when(visits.findLockedById(visit.getId())).thenReturn(Optional.of(visit));
    when(jdbc.queryForObject(
            contains("select count(*) from professional.professional"),
            eq(Integer.class),
            eq(doctor),
            eq(person),
            eq(org),
            eq(org)))
        .thenReturn(1);
    when(jdbc.queryForObject(contains("select timezone"), eq(String.class), eq(org)))
        .thenReturn("UTC");
    return visit;
  }

  @Test
  void firstEncounterStartsCoverageAndRepeatedStartCannotResetIt() {
    var visit = visit();
    var started = service.start(visit.getId(), 0);
    assertEquals("IN_PROGRESS", started.status());
    Instant initialExpiry = visit.episode().getExpiresAt();
    assertEquals(visit.getStartedAt().plus(Duration.ofDays(7)), initialExpiry);
    assertThrows(
        org.novasos.healthysv2.shared.api.error.BusinessRuleException.class,
        () -> service.start(visit.getId(), 0));
    assertEquals(initialExpiry, visit.episode().getExpiresAt());
  }

  @Test
  void revocationPreventsStartAndLeavesFollowUpClockUnset() {
    var visit = visit();
    doThrow(new AccessDeniedException("PATIENT_REVOKED_ACCESS"))
        .when(access)
        .requireAccess(any(), eq("MEDICAL_RECORD"), eq("WRITE"));
    assertThrows(AccessDeniedException.class, () -> service.start(visit.getId(), 0));
    assertNull(visit.episode().getStartedAt());
    assertEquals(CareVisit.Status.SCHEDULED, visit.getStatus());
  }

  @Test
  void staleVisitVersionCannotStartCoverage() {
    var visit = visit();
    assertThrows(ConflictException.class, () -> service.start(visit.getId(), 1));
    assertNull(visit.episode().getStartedAt());
    verifyNoInteractions(access);
  }

  @Test
  void nonAssignedDoctorCannotStartEncounter() {
    var visit = visit();
    when(jdbc.queryForObject(
            contains("select count(*) from professional.professional"),
            eq(Integer.class),
            eq(doctor),
            eq(person),
            eq(org),
            eq(org)))
        .thenReturn(0);
    assertThrows(AccessDeniedException.class, () -> service.start(visit.getId(), 0));
    assertNull(visit.episode().getStartedAt());
  }

  @SuppressWarnings("unchecked")
  private void reportState(CareVisit visit, String status, String diagnosis) throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.next()).thenReturn(true);
    when(rs.getLong("version")).thenReturn(4L);
    when(rs.getInt("current_revision")).thenReturn(1);
    when(rs.getString("status")).thenReturn(status);
    when(rs.getString("reason")).thenReturn("Fever");
    when(rs.getString("observations")).thenReturn("Examined");
    when(rs.getString("diagnosis")).thenReturn(diagnosis);
    when(rs.getString("treatment")).thenReturn("");
    when(rs.getString("follow_up")).thenReturn("");
    doAnswer(call -> ((ResultSetExtractor<?>) call.getArgument(1)).extractData(rs))
        .when(jdbc)
        .query(
            eq("select * from care.visit_report where visit_id=?"),
            any(ResultSetExtractor.class),
            eq(visit.getId()));
  }

  @Test
  void validatedReportCannotBeSilentlyOverwritten() throws Exception {
    var visit = visit();
    service.start(visit.getId(), 0);
    visit.complete(Instant.now());
    reportState(visit, "VALIDATED", "Viral infection");
    assertThrows(
        org.novasos.healthysv2.shared.api.error.BusinessRuleException.class,
        () ->
            service.save(
                visit.getId(),
                new EncounterDtos.Draft(4, "New reason", "New exam", "New diagnosis", "", "")));
    verify(jdbc, never()).update(contains("update care.visit_report"), any(Object[].class));
  }

  @Test
  void incompleteReportDoesNotCompleteVisit() throws Exception {
    var visit = visit();
    service.start(visit.getId(), 0);
    reportState(visit, "DRAFT", "");
    assertThrows(
        org.novasos.healthysv2.shared.api.error.BusinessRuleException.class,
        () -> service.finalizeReport(visit.getId(), 4));
    assertEquals(CareVisit.Status.IN_PROGRESS, visit.getStatus());
    verify(jdbc, never())
        .update(contains("insert into care.visit_report_revision"), any(Object[].class));
  }

  @Test
  void unownedAiProposalCannotBeRecordedAsReportProvenance() throws Exception {
    var visit = visit();
    service.start(visit.getId(), 0);
    reportState(visit, "DRAFT", "Existing diagnosis");
    var ai = mock(AiJobService.class);
    service.setAiJobs(ai);
    UUID job = UUID.randomUUID();
    doThrow(new AccessDeniedException("ASSIGNED_DOCTOR_REQUIRED"))
        .when(ai)
        .requireReportProvenance(visit.getId(), job);
    assertThrows(
        AccessDeniedException.class,
        () ->
            service.save(
                visit.getId(),
                new EncounterDtos.Draft(4, "Fever", "Exam", "Diagnosis", "", "", job)));
    verify(jdbc, never()).update(contains("update care.visit_report"), any(Object[].class));
  }

  @Test
  void editedAiDraftKeepsItsSourceJobWithoutAutomaticValidation() throws Exception {
    var visit = visit();
    service.start(visit.getId(), 0);
    reportState(visit, "DRAFT", "Existing diagnosis");
    var ai = mock(AiJobService.class);
    service.setAiJobs(ai);
    UUID job = UUID.randomUUID();
    when(ai.requireReportProvenance(visit.getId(), job)).thenReturn(job);
    service.save(
        visit.getId(),
        new EncounterDtos.Draft(
            4, "Edited reason", "Doctor corrected examination", "Doctor diagnosis", "", "", job));
    verify(jdbc)
        .update(
            eq("update care.visit_report set ai_job_id=? where visit_id=?"),
            eq(job),
            eq(visit.getId()));
    verify(jdbc, never())
        .update(contains("insert into care.visit_report_revision"), any(Object[].class));
    assertEquals(CareVisit.Status.IN_PROGRESS, visit.getStatus());
  }

  @Test
  void patientWithGlobalDoctorRoleCannotReadDraftWithoutClinicalOrganization() {
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(person, null, Set.of("PATIENT", "DOCTOR")));
    assertThrows(AccessDeniedException.class, () -> service.read(UUID.randomUUID()));
    verifyNoInteractions(visits, access, jdbc);
  }

  @Test
  void staleGlobalDoctorRoleDoesNotReplacePersistedClinicalMembershipForDraftRead() {
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(person, org, Set.of("PATIENT", "DOCTOR")));
    assertThrows(AccessDeniedException.class, () -> service.read(UUID.randomUUID()));
    verifyNoInteractions(visits, access);
    verify(licensing).requireMember(org);
    verify(licensing).requireActive(org);
  }

  @Test
  void importedHiddenReportDoesNotBecomePatientVisibleThroughTheNewFacade() {
    var visit = visit();
    when(visits.findById(visit.getId())).thenReturn(Optional.of(visit));
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                person,
                null,
                Set.of("PATIENT"),
                UUID.randomUUID(),
                visit.episode().getPatientId()));
    var revision =
        new org.novasos.healthysv2.care.api.VisitReportReader.ValidatedVisitReport(
            visit.getId(),
            visit.episode().getId(),
            visit.episode().getPatientId(),
            org,
            doctor,
            "Doctor",
            1,
            UUID.randomUUID(),
            Instant.now(),
            "Historical reason",
            "Confidential exam",
            "Diagnosis",
            "",
            "",
            null,
            false);
    when(jdbc.query(
            contains("select r.*,concat"),
            any(org.springframework.jdbc.core.RowMapper.class),
            eq(visit.getId())))
        .thenReturn(List.of(revision));
    when(jdbc.queryForObject(
            contains("select count(*) from patient.patient"),
            eq(Integer.class),
            eq(visit.episode().getPatientId()),
            eq(person)))
        .thenReturn(1);
    assertThrows(AccessDeniedException.class, () -> service.validatedReport(visit.getId()));
  }

  @Test
  void clinicalHistoryRequiresEffectivePatientAccessBeforeReadingReports() {
    UUID patient = UUID.randomUUID();
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(person, org, Set.of("DOCTOR")));
    when(jdbc.queryForObject(
            contains("select count(*) from professional.professional"),
            eq(Integer.class),
            eq(org),
            eq(person),
            eq(org)))
        .thenReturn(1);
    doThrow(new AccessDeniedException("EXPIRED"))
        .when(access)
        .requireAccess(patient, "MEDICAL_RECORD", "READ");
    assertThrows(AccessDeniedException.class, () -> service.history(patient));
    verify(jdbc, never())
        .query(
            contains("from care.visit_report_revision"),
            any(org.springframework.jdbc.core.RowMapper.class),
            any(Object[].class));
  }

  @Test
  void clinicalHistoryDoesNotRequireAiEntitlementButRequiresRealRoster() {
    UUID patient = UUID.randomUUID();
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(person, org, Set.of("DOCTOR")));
    assertThrows(AccessDeniedException.class, () -> service.history(patient));
    verifyNoInteractions(access);
    when(jdbc.queryForObject(
            contains("select count(*) from professional.professional"),
            eq(Integer.class),
            eq(org),
            eq(person),
            eq(org)))
        .thenReturn(1);
    assertNotNull(service.history(patient));
    verify(access).requireAccess(patient, "MEDICAL_RECORD", "READ");
    verify(licensing, times(2)).requireActive(org);
    verify(licensing, times(2)).requireMember(org);
  }

  @Test
  void reportPermissionRevokedWhileWaitingForTheTenantLockStopsMutation() {
    var visit = visit();
    service.start(visit.getId(), 0);
    doThrow(new AccessDeniedException("REPORT_PERMISSION_REVOKED"))
        .when(permissions)
        .require(org, "reports", "UPDATE");
    assertThrows(
        AccessDeniedException.class,
        () ->
            service.save(
                visit.getId(), new EncounterDtos.Draft(0, "Fever", "Exam", "Diagnosis", "", "")));
    verify(jdbc, never()).update(contains("visit_report"), any(Object[].class));
  }
}
