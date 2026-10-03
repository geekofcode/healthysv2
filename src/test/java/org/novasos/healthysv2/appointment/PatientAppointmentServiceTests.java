package org.novasos.healthysv2.appointment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.novasos.healthysv2.appointment.api.PatientAppointmentDtos.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.identity.api.*;
import org.novasos.healthysv2.appointment.api.AppointmentDtos.*;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;

class PatientAppointmentServiceTests {
    final UUID subject = UUID.randomUUID(), person = UUID.randomUUID(), patient = UUID.randomUUID();
    final UUID professional = UUID.randomUUID(), organization = UUID.randomUUID();
    final PersonLookup identities = mock(PersonLookup.class);
    final AppointmentRepository repository = mock(AppointmentRepository.class);
    final AppointmentService booking = mock(AppointmentService.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AuditTrail audit = mock(AuditTrail.class);
    final PatientAppointmentService service = new PatientAppointmentService(identities, repository, booking, jdbc, audit);
    final Instant start = Instant.now().plus(Duration.ofDays(2));

    PatientAppointmentServiceTests() {
        var identity = mock(PersonResponse.class);
        when(identity.id()).thenReturn(person);
        when(identities.findMe(subject)).thenReturn(identity);
        when(jdbc.query(eq("select id from patient.patient where person_id=?"),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<UUID>>any(), eq(person))).thenReturn(patient);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
    }

    @Test void rejectsOtherPatientsDetailCancelAndRescheduleBeforeMutation() {
        Appointment other = entity(UUID.randomUUID());
        when(repository.findById(other.getId())).thenReturn(Optional.of(other));
        when(repository.findLockedById(other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.detail(subject, other.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancel(subject, other.getId(), new PatientCancelRequest(null))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reschedule(subject, other.getId(), new PatientRescheduleRequest(start, start.plusSeconds(1800), null))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(booking);
        verify(audit, times(3)).access(eq(person), eq(patient), isNull(), eq("APPOINTMENT"), eq(other.getId()), eq("DENIED"), anyString(), any());
    }

    @Test void missingIdentityOrPatientNeverPerformsUnfilteredSearchOrProvisioning() {
        when(identities.findMe(subject)).thenThrow(new ResourceNotFoundException("Person", subject));
        assertThatThrownBy(() -> service.list(subject, "upcoming", 0, 20)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(repository, booking);
    }

    @Test void createDerivesPatientAndRejectsMissingOrganizationRegistration() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.create(subject, new PatientBookingRequest(organization, professional, start, start.plusSeconds(1800), "Checkup")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(booking);
    }

    @Test void createsOnlyForStrictlyResolvedPatientWithFixedType() {
        Appointment created = entity(patient);
        AppointmentResponse response = mock(AppointmentResponse.class);
        when(response.id()).thenReturn(created.getId());
        when(booking.create(any())).thenReturn(response);
        when(repository.findById(created.getId())).thenReturn(Optional.of(created));
        service.create(subject, new PatientBookingRequest(organization, professional, start, start.plusSeconds(1800), "Checkup"));
        var captured = org.mockito.ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(booking).create(captured.capture());
        assertThat(captured.getValue().patientId()).isEqualTo(patient);
        assertThat(captured.getValue().type()).isEqualTo("CONSULTATION");
        verify(audit).change(eq(person), eq(organization), eq("APPOINTMENT"), eq("Appointment"), eq(created.getId()), eq("CREATE"), isNull(), any());
    }

    @Test void cancellationAndRescheduleAreOwnedLockedAndAuditActualState() {
        Appointment own = entity(patient);
        when(repository.findLockedById(own.getId())).thenReturn(Optional.of(own));
        when(booking.reschedule(eq(own.getId()), any())).thenAnswer(invocation -> {
            RescheduleRequest request = invocation.getArgument(1);
            own.reschedule(request.scheduledStart(), request.scheduledEnd(), person, request.reason());
            return null;
        });
        when(booking.cancel(eq(own.getId()), any())).thenAnswer(invocation -> { own.cancel(person, "Changed plans"); return null; });
        var moved = service.reschedule(subject, own.getId(), new PatientRescheduleRequest(start.plusSeconds(3600), start.plusSeconds(5400), "Change"));
        assertThat(moved.status()).isEqualTo("RESCHEDULED");
        var cancelled = service.cancel(subject, own.getId(), new PatientCancelRequest("Changed plans"));
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.canCancel()).isFalse();
        assertThat(cancelled.canReschedule()).isFalse();
        verify(repository, times(2)).findLockedById(own.getId());
        verify(repository, times(2)).flush();
    }

    @Test void startedAndCheckedInAppointmentsCannotBeChanged() {
        Appointment started = Appointment.create(patient, professional, organization, null, "CONSULTATION", Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1800), null, person);
        Appointment checkedIn = entity(patient);
        checkedIn.changeStatus("CHECKED_IN", person, null);
        for (var entity : List.of(started, checkedIn)) {
            when(repository.findLockedById(entity.getId())).thenReturn(Optional.of(entity));
            assertThatThrownBy(() -> service.cancel(subject, entity.getId(), new PatientCancelRequest(null))).isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.reschedule(subject, entity.getId(), new PatientRescheduleRequest(start, start.plusSeconds(1800), null))).isInstanceOf(BusinessRuleException.class);
        }
        verifyNoInteractions(booking);
    }

    @Test void pastTimesInvalidDurationsAndExcessiveAvailabilityRangeAreRejected() {
        assertThatThrownBy(() -> service.create(subject, new PatientBookingRequest(organization, professional, Instant.now().minusSeconds(3600), start, null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.create(subject, new PatientBookingRequest(organization, professional, start, start.plusSeconds(600), null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.availability(subject, organization, professional, start, start.plus(Duration.ofDays(32)), null)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(booking);
    }

    @Test void availabilityExclusionCannotHideAnotherPatientsBooking() {
        Appointment other = entity(UUID.randomUUID());
        when(repository.findById(other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.availability(subject, organization, professional, start, start.plusSeconds(3600), other.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void availabilityClipsOldWindowsAndRemovesOccupiedSlots() throws Exception {
        Instant windowStart = start.minus(Duration.ofDays(1000));
        when(jdbc.query(contains("professional.professional_availability pa"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getTimestamp("start_at")).thenReturn(java.sql.Timestamp.from(windowStart));
                    when(rs.getTimestamp("end_at")).thenReturn(java.sql.Timestamp.from(start.plusSeconds(3600)));
                    when(rs.getDate("start_date")).thenReturn(java.sql.Date.valueOf(LocalDate.of(2020,1,1)));
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(rs,0));
                });
        when(jdbc.query(contains("select scheduled_start,scheduled_end"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getTimestamp(1)).thenReturn(java.sql.Timestamp.from(start));
                    when(rs.getTimestamp(2)).thenReturn(java.sql.Timestamp.from(start.plusSeconds(1800)));
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(rs,0));
                });
        var available = service.availability(subject,organization,professional,start,start.plusSeconds(3600),null);
        assertThat(available.slots()).containsExactly(new AvailableSlot(start.plusSeconds(1800),start.plusSeconds(3600)));
    }

    @Test void listsOnlyResolvedPatientAndUsesFixedTimeOrder() {
        when(repository.searchSelf(eq(patient),eq(false),any(Instant.class),any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> org.springframework.data.domain.Page.empty(invocation.getArgument(3)));
        var result = service.list(subject,"upcoming",0,20);
        assertThat(result.content()).isEmpty();
        var captured = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(repository).searchSelf(eq(patient),eq(false),any(Instant.class),captured.capture());
        assertThat(captured.getValue().getSort().getOrderFor("start").isAscending()).isTrue();
    }

    private Appointment entity(UUID owner) { return Appointment.create(owner, professional, organization, null, "CONSULTATION", start, start.plusSeconds(1800), "Checkup", person); }
}
