package org.novasos.healthysv2.professional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class ProfessionalTenantScopeTests {
  CurrentUserContext users = mock(CurrentUserContext.class);
  OrganizationPermissionAccess permissions = mock(OrganizationPermissionAccess.class);
  ProfessionalRepository repository = mock(ProfessionalRepository.class);
  JdbcTemplate db = mock(JdbcTemplate.class);
  ProfessionalService service =
      new ProfessionalService(
          repository,
          mock(ProfessionalLicenseRepository.class),
          mock(ProfessionalSpecialityRepository.class),
          mock(ProfessionalAssignmentRepository.class),
          mock(ProfessionalScheduleRepository.class),
          mock(ProfessionalAvailabilityRepository.class),
          db,
          users,
          mock(LicensingAccess.class),
          permissions);

  @Test
  void platformAdministratorCannotReadStaff() {
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of("PLATFORM_ADMIN")));
    assertThatThrownBy(() -> service.find(UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(repository, permissions);
  }

  @Test
  void hospitalDetailHidesOtherHospitalAssignments() {
    UUID org = UUID.randomUUID(), id = UUID.randomUUID(), person = UUID.randomUUID();
    when(users.current())
        .thenReturn(
            new CurrentUserContext.UserContext(UUID.randomUUID(), org, Set.of("HOSPITAL_ADMIN")));
    var p = Professional.create(person, "PRO-TEST", "DOCTOR", "ACTIVE");
    p.addAssignment(org, null, null, "Médecin", "1", LocalDate.now(), null, "ACTIVE");
    p.addAssignment(
        UUID.randomUUID(), null, null, "Autre poste", "2", LocalDate.now(), null, "ACTIVE");
    when(repository.findById(id)).thenReturn(Optional.of(p));
    when(db.queryForObject(anyString(), eq(Integer.class), eq(org), eq(person))).thenReturn(1);
    var response = service.find(id);
    assertThat(response.assignments()).hasSize(1);
    assertThat(response.assignments().getFirst().organizationId()).isEqualTo(org);
  }
}
