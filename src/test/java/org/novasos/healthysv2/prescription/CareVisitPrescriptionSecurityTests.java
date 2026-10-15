package org.novasos.healthysv2.prescription;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.care.api.VisitReportReader;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class CareVisitPrescriptionSecurityTests {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"DOCTOR", "NURSE"})
  void assignedVerifiedClinicianCanSavePrescriptionButStaleMembershipCannot(String role)
      throws Exception {
    UUID visit = UUID.randomUUID(),
        patient = UUID.randomUUID(),
        org = UUID.randomUUID(),
        person = UUID.randomUUID();
    var jdbc = mock(JdbcTemplate.class);
    var users = mock(CurrentUserContext.class);
    var access = mock(PatientAccessService.class);
    var permissions =
        mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class);
    when(users.current()).thenReturn(new CurrentUserContext.UserContext(person, org, Set.of(role)));
    var visitRow = mock(ResultSet.class);
    when(visitRow.getObject(1, UUID.class)).thenReturn(visit);
    when(visitRow.getObject(2, UUID.class)).thenReturn(patient);
    when(visitRow.getObject(3, UUID.class)).thenReturn(org);
    when(visitRow.getObject(4, UUID.class)).thenReturn(person);
    when(visitRow.getString(5)).thenReturn("IN_PROGRESS");
    when(visitRow.getTimestamp(6)).thenReturn(Timestamp.from(Instant.now().plusSeconds(60)));
    when(jdbc.query(contains("from care.care_visit"), any(RowMapper.class), eq(visit)))
        .thenAnswer(
            call -> java.util.List.of(((RowMapper<?>) call.getArgument(1)).mapRow(visitRow, 0)));
    var draft = mock(ResultSet.class);
    when(draft.next()).thenReturn(true);
    when(draft.getString("status")).thenReturn("DRAFT");
    when(draft.getString("items")).thenReturn("[]");
    when(jdbc.query(
            eq("select * from care.visit_prescription where visit_id=?"),
            any(ResultSetExtractor.class),
            eq(visit)))
        .thenAnswer(call -> ((ResultSetExtractor<?>) call.getArgument(1)).extractData(draft));
    when(jdbc.queryForObject(
            contains("select count(*) from professional.professional"),
            eq(Integer.class),
            eq(person),
            eq(visit),
            eq(org),
            eq(org)))
        .thenReturn(1);
    var service =
        new CareVisitPrescriptionService(
            jdbc,
            users,
            access,
            mock(LicensingAccess.class),
            mock(VisitReportReader.class),
            new ObjectMapper());
    service.configurePermissions(permissions);
    var request =
        new org.novasos.healthysv2.prescription.api.CareVisitPrescriptionDtos.SaveRequest(
            0, java.util.List.of(), null);
    service.save(visit, request);
    verify(access).requireAccess(patient, "PRESCRIPTIONS", "WRITE");
    verify(permissions).require(org, "prescriptions", "UPDATE");
    clearInvocations(jdbc, access);
    when(jdbc.queryForObject(
            contains("select count(*) from professional.professional"),
            eq(Integer.class),
            eq(person),
            eq(visit),
            eq(org),
            eq(org)))
        .thenReturn(0);
    assertThatThrownBy(() -> service.save(visit, request))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verify(jdbc, never())
        .update(contains("insert into care.visit_prescription"), any(Object[].class));
    verifyNoInteractions(access);
  }

  @Test
  void patientWithGlobalDoctorRoleAndNoClinicalContextCannotReadOwnDraft() throws Exception {
    UUID visit = UUID.randomUUID(),
        patient = UUID.randomUUID(),
        org = UUID.randomUUID(),
        person = UUID.randomUUID();
    var jdbc = mock(JdbcTemplate.class);
    var users = mock(CurrentUserContext.class);
    var licensing = mock(LicensingAccess.class);
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(person, null, Set.of("PATIENT", "DOCTOR")));
    var visitRow = mock(ResultSet.class);
    when(visitRow.getObject(1, UUID.class)).thenReturn(visit);
    when(visitRow.getObject(2, UUID.class)).thenReturn(patient);
    when(visitRow.getObject(3, UUID.class)).thenReturn(org);
    when(visitRow.getObject(4, UUID.class)).thenReturn(person);
    when(visitRow.getString(5)).thenReturn("IN_PROGRESS");
    when(visitRow.getTimestamp(6)).thenReturn(Timestamp.from(Instant.now().plusSeconds(60)));
    when(jdbc.query(anyString(), any(RowMapper.class), eq(visit)))
        .thenAnswer(
            call -> java.util.List.of(((RowMapper<?>) call.getArgument(1)).mapRow(visitRow, 0)));
    var draft = mock(ResultSet.class);
    when(draft.next()).thenReturn(true);
    when(draft.getLong("version")).thenReturn(2L);
    when(draft.getInt("current_revision")).thenReturn(0);
    when(draft.getString("status")).thenReturn("DRAFT");
    when(draft.getString("items")).thenReturn("[]");
    when(jdbc.query(
            eq("select * from care.visit_prescription where visit_id=?"),
            any(ResultSetExtractor.class),
            eq(visit)))
        .thenAnswer(call -> ((ResultSetExtractor<?>) call.getArgument(1)).extractData(draft));
    var service =
        new CareVisitPrescriptionService(
            jdbc,
            users,
            mock(PatientAccessService.class),
            licensing,
            mock(VisitReportReader.class),
            new ObjectMapper());
    assertThatThrownBy(() -> service.read(visit)).isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(licensing);
  }
}
