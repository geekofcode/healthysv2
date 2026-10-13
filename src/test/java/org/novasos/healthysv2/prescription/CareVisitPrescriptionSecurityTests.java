package org.novasos.healthysv2.prescription;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.care.api.VisitReportReader;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.ResultSetExtractor;

class CareVisitPrescriptionSecurityTests {
    @Test void patientWithGlobalDoctorRoleAndNoClinicalContextCannotReadOwnDraft() throws Exception {
        UUID visit=UUID.randomUUID(),patient=UUID.randomUUID(),org=UUID.randomUUID(),person=UUID.randomUUID();
        var jdbc=mock(JdbcTemplate.class);var users=mock(CurrentUserContext.class);var licensing=mock(LicensingAccess.class);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(person,null,Set.of("PATIENT","DOCTOR")));
        var visitRow=mock(ResultSet.class);
        when(visitRow.getObject(1,UUID.class)).thenReturn(visit);when(visitRow.getObject(2,UUID.class)).thenReturn(patient);
        when(visitRow.getObject(3,UUID.class)).thenReturn(org);when(visitRow.getObject(4,UUID.class)).thenReturn(person);
        when(visitRow.getString(5)).thenReturn("IN_PROGRESS");when(visitRow.getTimestamp(6)).thenReturn(Timestamp.from(Instant.now().plusSeconds(60)));
        when(jdbc.query(anyString(),any(RowMapper.class),eq(visit))).thenAnswer(call->java.util.List.of(((RowMapper<?>)call.getArgument(1)).mapRow(visitRow,0)));
        var draft=mock(ResultSet.class);when(draft.next()).thenReturn(true);when(draft.getLong("version")).thenReturn(2L);
        when(draft.getInt("current_revision")).thenReturn(0);when(draft.getString("status")).thenReturn("DRAFT");
        when(draft.getString("items")).thenReturn("[]");
        when(jdbc.query(eq("select * from care.visit_prescription where visit_id=?"),any(ResultSetExtractor.class),eq(visit)))
                .thenAnswer(call->((ResultSetExtractor<?>)call.getArgument(1)).extractData(draft));
        var service=new CareVisitPrescriptionService(jdbc,users,mock(PatientAccessService.class),licensing,mock(VisitReportReader.class),new ObjectMapper());
        assertThatThrownBy(()->service.read(visit)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(licensing);
    }
}
