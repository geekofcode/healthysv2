package org.novasos.healthysv2.appointment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.novasos.healthysv2.appointment.api.AppointmentDtos.*;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AppointmentPatientRegressionTests {
    final AppointmentRepository repository = mock(AppointmentRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AppointmentService service = new AppointmentService(repository, jdbc);
    final UUID subject = UUID.randomUUID(), person = UUID.randomUUID(), patient = UUID.randomUUID(), professional = UUID.randomUUID(), organization = UUID.randomUUID();
    final Instant start = Instant.now().plusSeconds(7200);

    @BeforeEach void login() {
        var jwt = Jwt.withTokenValue("patient").header("alg", "RS256").subject(subject.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
        when(jdbc.query(eq("select id from identity.person where keycloak_user_id=?"), org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(), eq(subject))).thenReturn(person);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void unlinkedMineReturnsEmptyWithoutAnyGlobalSearchOrPersonIdFallback() {
        when(jdbc.query(eq("select id from identity.person where keycloak_user_id=?"), org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(), eq(subject))).thenReturn(null);
        assertThat(service.mine(null, null, PageRequest.of(0, 20)).content()).isEmpty();
        verifyNoInteractions(repository);
        verify(jdbc, never()).queryForObject(contains("identity.person where id="), eq(Integer.class), any(Object[].class));
    }

    @Test void personWithoutPatientOrProfessionalNeverPerformsUnfilteredSearch() {
        assertThat(service.mine(null, null, PageRequest.of(0, 20)).content()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test void patientRoleCannotFallBackToProfessionalPatientList() {
        when(jdbc.query(eq("select id from professional.professional where person_id=?"),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),eq(person)))
                .thenReturn(professional);
        assertThat(service.mine(null,null,PageRequest.of(0,20)).content()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test void mineKeepsPatientScopeAndAppliesStatusBeforePagination() {
        when(jdbc.query(eq("select id from patient.patient where person_id=?"),org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(),eq(person))).thenReturn(patient);
        var pageable=PageRequest.of(1,2);
        Instant from=start.minusSeconds(3600),to=start.plusSeconds(3600);
        when(repository.search(patient,null,"CANCELLED",from,to,pageable)).thenReturn(org.springframework.data.domain.Page.empty(pageable));
        service.mine(from,to," CANCELLED ",pageable);
        verify(repository).search(patient,null,"CANCELLED",from,to,pageable);
    }

    @Test void legacyCancelRescheduleAndUpdateRejectOtherPatient() {
        Appointment other = Appointment.create(UUID.randomUUID(), professional, organization, null, "CONSULTATION", start, start.plusSeconds(1800), null, person);
        when(repository.findLockedById(other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.cancel(other.getId(), new CancelRequest(null))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reschedule(other.getId(), new RescheduleRequest(start, start.plusSeconds(1800), null, null))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(other.getId(), new AppointmentUpdateRequest(null, "CONSULTATION", start, start.plusSeconds(1800), null, null))).isInstanceOf(AccessDeniedException.class);
        assertThat(other.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test void legacyPatientCannotRescheduleOrUpdateIntoPast() {
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        Appointment own = Appointment.create(patient,professional,organization,null,"CONSULTATION",start,start.plusSeconds(1800),null,person);
        when(repository.findLockedById(own.getId())).thenReturn(Optional.of(own));
        Instant past = Instant.now().minusSeconds(3600);
        assertThatThrownBy(() -> service.reschedule(own.getId(),new RescheduleRequest(past,past.plusSeconds(1800),null,null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.update(own.getId(),new AppointmentUpdateRequest(null,"CONSULTATION",past,past.plusSeconds(1800),null,null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(own.getStart()).isEqualTo(start);
    }

    @Test void bookingConflictIsCheckedAfterProfessionalRowLockAndReturnsConflict() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        assertThatThrownBy(() -> service.create(new AppointmentRequest(patient, professional, organization, null, "CONSULTATION", start, start.plusSeconds(1800), null, null)))
                .isInstanceOf(ConflictException.class);
        var order = inOrder(jdbc);
        order.verify(jdbc).query(eq("select id from professional.professional where id=? for update"), org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(), eq(professional));
        order.verify(jdbc).queryForObject(startsWith("select count(*) from professional.professional_assignment"), eq(Integer.class), any(Object[].class));
        order.verify(jdbc).queryForObject(contains("professional.professional_availability"), eq(Integer.class), any(Object[].class));
        order.verify(jdbc).queryForObject(contains("appointment.appointment"), eq(Integer.class), any(Object[].class));
        verify(repository, never()).saveAndFlush(any());
    }
}
