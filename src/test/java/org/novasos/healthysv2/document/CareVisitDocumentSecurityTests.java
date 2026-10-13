package org.novasos.healthysv2.document;
import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
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
import org.novasos.healthysv2.prescription.api.CareVisitPrescriptionReader;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

class CareVisitDocumentSecurityTests {
    @Test void generationRejectsUnsupportedTypeBeforeReadingClinicalData() {
        var jdbc=mock(JdbcTemplate.class);var storage=mock(DocumentStorage.class);var reports=mock(VisitReportReader.class);
        var service=new CareVisitDocumentService(jdbc,mock(CurrentUserContext.class),mock(PatientAccessService.class),mock(LicensingAccess.class),reports,mock(CareVisitPrescriptionReader.class),new CareVisitPdf(),storage,mock(HealthDocumentRepository.class));
        assertThatThrownBy(()->service.generate(UUID.randomUUID(),"INVOICE")).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(jdbc,reports,storage);
    }
    @Test void patientWithGlobalDoctorRoleStillFiltersUnpublishedDocumentMetadata() throws Exception {
        UUID visit=UUID.randomUUID(),patient=UUID.randomUUID(),org=UUID.randomUUID(),person=UUID.randomUUID();
        var jdbc=mock(JdbcTemplate.class);var users=mock(CurrentUserContext.class);var access=mock(PatientAccessService.class);
        var licensing=mock(LicensingAccess.class);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(person,null,Set.of("PATIENT","DOCTOR")));
        var rs=mock(ResultSet.class);when(rs.getObject(1,UUID.class)).thenReturn(visit);when(rs.getObject(2,UUID.class)).thenReturn(patient);
        when(rs.getObject(3,UUID.class)).thenReturn(org);when(rs.getObject(4,UUID.class)).thenReturn(person);
        when(rs.getTimestamp(5)).thenReturn(Timestamp.from(Instant.now().plusSeconds(60)));
        when(rs.getString(6)).thenReturn("Patient");when(rs.getString(7)).thenReturn("Hospital");
        when(jdbc.query(anyString(),any(RowMapper.class),eq(visit))).thenAnswer(call->java.util.List.of(((RowMapper<?>)call.getArgument(1)).mapRow(rs,0)));
        when(jdbc.query(contains("from care.visit_document"),any(RowMapper.class),eq(visit),eq(true))).thenReturn(java.util.List.of());
        var service=new CareVisitDocumentService(jdbc,users,access,licensing,mock(VisitReportReader.class),mock(CareVisitPrescriptionReader.class),new CareVisitPdf(),mock(DocumentStorage.class),mock(HealthDocumentRepository.class));
        assertThat(service.list(visit)).isEmpty();
        verify(jdbc).query(contains("d.patient_visible=true"),any(RowMapper.class),eq(visit),eq(true));
        verify(access).requireAccess(patient,"DOCUMENTS","READ");verifyNoInteractions(licensing);
    }
    @Test void differentDoctorCannotGenerateEvenWithDoctorRole() throws Exception {
        UUID visit=UUID.randomUUID(),patient=UUID.randomUUID(),org=UUID.randomUUID(),assigned=UUID.randomUUID();
        var jdbc=mock(JdbcTemplate.class);var users=mock(CurrentUserContext.class);var access=mock(PatientAccessService.class);
        var storage=mock(DocumentStorage.class);var reports=mock(VisitReportReader.class);var licensing=mock(LicensingAccess.class);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),org,Set.of("DOCTOR")));
        var rs=mock(ResultSet.class);when(rs.getObject(1,UUID.class)).thenReturn(visit);when(rs.getObject(2,UUID.class)).thenReturn(patient);
        when(rs.getObject(3,UUID.class)).thenReturn(org);when(rs.getObject(4,UUID.class)).thenReturn(assigned);
        when(rs.getTimestamp(5)).thenReturn(Timestamp.from(Instant.now().plusSeconds(300)));
        when(rs.getString(6)).thenReturn("Patient");when(rs.getString(7)).thenReturn("Hospital");
        when(jdbc.query(anyString(),any(RowMapper.class),eq(visit))).thenAnswer(invocation->java.util.List.of(((RowMapper<?>)invocation.getArgument(1)).mapRow(rs,0)));
        var service=new CareVisitDocumentService(jdbc,users,access,licensing,reports,mock(CareVisitPrescriptionReader.class),new CareVisitPdf(),storage,mock(HealthDocumentRepository.class));
        assertThatThrownBy(()->service.generate(visit,"REPORT")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(access,licensing,reports,storage);
    }
}
