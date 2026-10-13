package org.novasos.healthysv2.patient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
class TemporaryPatientAccessTests {
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final CurrentUserContext users=mock(CurrentUserContext.class);
 private final LicensingAccess licensing=mock(LicensingAccess.class);
 private final TemporaryPatientAccessService service=new TemporaryPatientAccessService(jdbc,users,licensing,new PatientAccessPolicy(Duration.ofHours(48),Duration.ofHours(1),Duration.ofHours(1)),mock(PatientAuditService.class));
 @Test void doctorCannotUseEmergencyBypass(){when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(),UUID.randomUUID(),Set.of("DOCTOR")));assertThatThrownBy(()->service.emergency(UUID.randomUUID(),"Urgency")).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(jdbc);}
 @Test void anotherPatientCannotAcceptRequest(){UUID person=UUID.randomUUID();when(users.current()).thenReturn(new CurrentUserContext.UserContext(person,null,Set.of("PATIENT")));when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);assertThatThrownBy(()->service.accept(UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(AccessDeniedException.class).hasMessage("Patient owner required");verify(jdbc,never()).update(anyString(),any(Object[].class));}
 @Test void accessPolicyRejectsUnboundedConfiguration(){assertThatThrownBy(()->new PatientAccessPolicy(Duration.ZERO,Duration.ofHours(1),Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);}
}
