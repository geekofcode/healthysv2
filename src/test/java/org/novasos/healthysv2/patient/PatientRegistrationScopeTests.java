package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.patient.api.PatientDtos.RegistrationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class PatientRegistrationScopeTests {
    private final PatientRepository patients = mock(PatientRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CurrentUserContext users = mock(CurrentUserContext.class);
    private final PatientService service = new PatientService(patients, jdbc, users, mock(PatientIdentityService.class));

    @Test void localAdministratorCannotAttachAPatientToAnotherHospital() {
        UUID selected = UUID.randomUUID();
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), selected, Set.of("HOSPITAL_ADMIN")));
        var request = new RegistrationRequest(UUID.randomUUID(), "LOCAL-1", null, "ACTIVE");
        assertThatThrownBy(() -> service.addRegistration(UUID.randomUUID(), request))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(patients, jdbc);
    }

    @Test void localAdministratorCannotRemoveAnotherHospitalsRegistration() {
        UUID selected = UUID.randomUUID();
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), selected, Set.of("HOSPITAL_ADMIN")));
        var patient = Patient.create(UUID.randomUUID());
        var foreign = patient.addRegistration(UUID.randomUUID(), "LOCAL-1", null, "ACTIVE");
        UUID registration = UUID.randomUUID();
        ReflectionTestUtils.setField(foreign, "id", registration);
        when(patients.findById(patient.getId())).thenReturn(Optional.of(patient));
        assertThatThrownBy(() -> service.deleteRegistration(patient.getId(), registration))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(patient.getRegistrations()).containsExactly(foreign);
    }
}
