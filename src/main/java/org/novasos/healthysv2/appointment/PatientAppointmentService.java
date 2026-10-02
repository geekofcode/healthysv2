package org.novasos.healthysv2.appointment;

import static org.novasos.healthysv2.appointment.api.PatientAppointmentDtos.*;
import java.time.*;
import java.util.*;
import org.novasos.healthysv2.appointment.api.AppointmentDtos.*;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.identity.api.PersonLookup;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class PatientAppointmentService {
    private static final Set<String> CHANGEABLE = Set.of("SCHEDULED", "CONFIRMED", "RESCHEDULED");
    private final PersonLookup identities;
    private final AppointmentRepository appointments;
    private final AppointmentService booking;
    private final JdbcTemplate jdbc;
    private final AuditTrail audit;

    PatientAppointmentService(PersonLookup identities, AppointmentRepository appointments,
                              AppointmentService booking, JdbcTemplate jdbc, AuditTrail audit) {
        this.identities = identities; this.appointments = appointments;
        this.booking = booking; this.jdbc = jdbc; this.audit = audit;
    }

    @Transactional(readOnly = true)
    PageResponse<PatientAppointmentResponse> list(UUID subject, String view, int page, int size) {
        var self = self(subject);
        if (!Set.of("upcoming", "past").contains(view) || page < 0 || size < 1 || size > 100) {
            throw invalid("INVALID_APPOINTMENT_PAGE");
        }
        boolean past = "past".equals(view);
        var pageable = PageRequest.of(page, size, Sort.by(past ? Sort.Direction.DESC : Sort.Direction.ASC, "start", "id"));
        var result = PageResponse.from(appointments.searchSelf(self.patientId(), past, Instant.now(), pageable).map(this::response));
        accessed(self, null, "LIST");
        return result;
    }

    @Transactional(readOnly = true)
    PatientAppointmentResponse detail(UUID subject, UUID id) {
        var self = self(subject);
        var appointment = owned(self, id, false);
        accessed(self, appointment, "READ");
        return response(appointment);
    }

    @Transactional(readOnly = true)
    BookingOptions options(UUID subject) {
        var self = self(subject);
        var organizations = jdbc.query("""
                select distinct o.id,o.name from organization.organization o
                join patient.patient_registration r on r.organization_id=o.id
                where r.patient_id=? and r.status='ACTIVE' and o.status='ACTIVE' order by o.name,o.id
                """, (rs, i) -> new OrganizationOption(rs.getObject("id", UUID.class), rs.getString("name")), self.patientId());
        var professionals = jdbc.query("""
                select distinct p.id,a.organization_id,concat_ws(' ',person.first_name,person.last_name) as name,p.professional_type
                from professional.professional p join identity.person person on person.id=p.person_id
                join professional.professional_assignment a on a.professional_id=p.id
                join organization.organization o on o.id=a.organization_id
                join patient.patient_registration r on r.organization_id=a.organization_id
                where r.patient_id=? and r.status='ACTIVE' and o.status='ACTIVE' and p.status='ACTIVE'
                and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date)
                order by name,p.id,a.organization_id
                """, (rs, i) -> new ProfessionalOption(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                        rs.getString("name"), rs.getString("professional_type")), self.patientId());
        return new BookingOptions(organizations, professionals);
    }

    @Transactional(readOnly = true)
    AvailableSlots availability(UUID subject, UUID organization, UUID professional, Instant from, Instant to, UUID exclude) {
        var self = self(subject);
        validateRange(from, to);
        requireBookingContext(self, organization, professional);
        if (exclude != null) {
            var current = owned(self, exclude, false);
            requireChangeable(current);
            if (!organization.equals(current.getOrganizationId()) || !professional.equals(current.getProfessionalId())) {
                throw new AccessDeniedException("The appointment is not in this booking context");
            }
        }
        var windows = windows(professional, organization, from, to);
        var occupied = occupied(professional, from, to, exclude);
        var slots = new LinkedHashSet<AvailableSlot>();
        Instant earliest = from.isAfter(Instant.now()) ? from : Instant.now();
        for (var window : windows) {
            for (Instant start = firstSlot(window.start(), earliest); !start.plusSeconds(1800).isAfter(window.end()) && start.isBefore(to); start = start.plusSeconds(1800)) {
                Instant end = start.plusSeconds(1800);
                if (start.isBefore(earliest) || end.isAfter(to) || !window.coversAssignment(start, end)) continue;
                Instant candidateStart = start;
                if (occupied.stream().anyMatch(busy -> candidateStart.isBefore(busy.end()) && end.isAfter(busy.start()))) continue;
                slots.add(new AvailableSlot(start, end));
                if (slots.size() >= 200) return new AvailableSlots(List.copyOf(slots));
            }
        }
        return new AvailableSlots(List.copyOf(slots));
    }

    PatientAppointmentResponse create(UUID subject, PatientBookingRequest request) {
        var self = self(subject);
        requireFuture(request.scheduledStart(), request.scheduledEnd());
        requireBookingContext(self, request.organizationId(), request.professionalId());
        var saved = booking.create(new AppointmentRequest(self.patientId(), request.professionalId(), request.organizationId(),
                null, "CONSULTATION", request.scheduledStart(), request.scheduledEnd(), request.reason(), null));
        var entity = appointments.findById(saved.id()).orElseThrow(() -> new ResourceNotFoundException("Appointment", saved.id()));
        changed(self, entity, "CREATE");
        return response(entity);
    }

    PatientAppointmentResponse cancel(UUID subject, UUID id, PatientCancelRequest request) {
        var self = self(subject);
        var entity = owned(self, id, true);
        requireChangeable(entity);
        booking.cancel(id, new CancelRequest(request.reason()));
        appointments.flush();
        changed(self, entity, "CANCEL");
        return response(entity);
    }

    PatientAppointmentResponse reschedule(UUID subject, UUID id, PatientRescheduleRequest request) {
        var self = self(subject);
        var entity = owned(self, id, true);
        requireChangeable(entity);
        requireFuture(request.scheduledStart(), request.scheduledEnd());
        requireBookingContext(self, entity.getOrganizationId(), entity.getProfessionalId());
        booking.reschedule(id, new RescheduleRequest(request.scheduledStart(), request.scheduledEnd(), request.reason(), null));
        appointments.flush();
        changed(self, entity, "RESCHEDULE");
        return response(entity);
    }

    private Self self(UUID subject) {
        var person = identities.findMe(subject);
        UUID patient = jdbc.query("select id from patient.patient where person_id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, person.id());
        if (patient == null) throw new ResourceNotFoundException("Patient for person", person.id());
        return new Self(person.id(), patient);
    }

    private Appointment owned(Self self, UUID id, boolean locked) {
        var found = locked ? appointments.findLockedById(id) : appointments.findById(id);
        var entity = found.orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        if (!self.patientId().equals(entity.getPatientId())) {
            audit.access(self.personId(), self.patientId(), null, "APPOINTMENT", id, "DENIED", "PATIENT_SELF_REQUIRED", Map.of("allowed", false));
            throw new AccessDeniedException("Patient can only access their own appointments");
        }
        return entity;
    }

    private void requireBookingContext(Self self, UUID organization, UUID professional) {
        if (!exists("""
                select count(*) from patient.patient_registration r join organization.organization o on o.id=r.organization_id
                where r.patient_id=? and r.organization_id=? and r.status='ACTIVE' and o.status='ACTIVE'
                """, self.patientId(), organization)) throw new AccessDeniedException("Patient is not registered in the organization");
        if (!exists("""
                select count(*) from professional.professional p join professional.professional_assignment a on a.professional_id=p.id
                where p.id=? and a.organization_id=? and p.status='ACTIVE' and a.status='ACTIVE'
                and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date)
                """, professional, organization)) throw invalid("PROFESSIONAL_UNAVAILABLE");
    }

    private List<Window> windows(UUID professional, UUID organization, Instant from, Instant to) {
        return jdbc.query("""
                select distinct pa.start_at,pa.end_at,a.start_date,a.end_date from professional.professional_availability pa
                join professional.professional_assignment a on a.id=pa.assignment_id
                where a.professional_id=? and a.organization_id=? and a.status='ACTIVE' and pa.status='AVAILABLE'
                and pa.start_at<? and pa.end_at>? order by pa.start_at,pa.end_at,a.start_date,a.end_date limit 500
                """, (rs, i) -> new Window(rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant(),
                rs.getDate("start_date").toLocalDate(), rs.getDate("end_date") == null ? null : rs.getDate("end_date").toLocalDate()),
                professional, organization, java.sql.Timestamp.from(to), java.sql.Timestamp.from(from));
    }

    private List<Busy> occupied(UUID professional, Instant from, Instant to, UUID exclude) {
        return jdbc.query("""
                select scheduled_start,scheduled_end from appointment.appointment where professional_id=?
                and status in ('SCHEDULED','CONFIRMED','RESCHEDULED','CHECKED_IN','IN_PROGRESS')
                and scheduled_start<? and scheduled_end>? and (?::uuid is null or id<>?::uuid)
                """, (rs, i) -> new Busy(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()),
                professional, java.sql.Timestamp.from(to), java.sql.Timestamp.from(from), exclude, exclude);
    }

    private PatientAppointmentResponse response(Appointment appointment) {
        String professionalName = jdbc.query("""
                select concat_ws(' ',person.first_name,person.last_name) from professional.professional p
                join identity.person person on person.id=p.person_id where p.id=?
                """, rs -> rs.next() ? rs.getString(1) : null, appointment.getProfessionalId());
        String organizationName = jdbc.query("select name from organization.organization where id=?",
                rs -> rs.next() ? rs.getString(1) : null, appointment.getOrganizationId());
        boolean changeable = canChange(appointment);
        return new PatientAppointmentResponse(appointment.getId(), appointment.getNumber(), appointment.getPatientId(),
                appointment.getProfessionalId(), professionalName, appointment.getOrganizationId(), organizationName,
                appointment.getType(), appointment.getStart(), appointment.getEnd(), appointment.getReason(), appointment.getStatus(),
                appointment.getVersion(), changeable, changeable);
    }

    private Instant firstSlot(Instant windowStart, Instant earliest) {
        if (!windowStart.isBefore(earliest)) return windowStart;
        long seconds = Duration.between(windowStart, earliest).getSeconds();
        long chunks = (seconds + 1799) / 1800;
        Instant candidate = windowStart.plusSeconds(chunks * 1800);
        return candidate.isBefore(earliest) ? candidate.plusSeconds(1800) : candidate;
    }
    private boolean canChange(Appointment appointment) { return CHANGEABLE.contains(appointment.getStatus()) && appointment.getStart().isAfter(Instant.now()); }
    private void requireChangeable(Appointment appointment) { if (!canChange(appointment)) throw invalid("APPOINTMENT_CANNOT_CHANGE"); }
    private void requireFuture(Instant start, Instant end) {
        if (start == null || end == null || !start.isAfter(Instant.now()) || !end.isAfter(start)
                || !Duration.between(start, end).equals(Duration.ofMinutes(30))) throw invalid("INVALID_APPOINTMENT_SLOT");
    }
    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from) || !to.isAfter(Instant.now())
                || from.isBefore(Instant.now().minus(Duration.ofMinutes(5)))
                || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) throw invalid("INVALID_AVAILABILITY_RANGE");
    }
    private boolean exists(String sql, Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, args)) > 0; }
    private BusinessRuleException invalid(String code) { return new BusinessRuleException(code, "error.appointment.unavailable"); }
    private void accessed(Self self, Appointment appointment, String action) {
        audit.access(self.personId(), self.patientId(), appointment == null ? null : appointment.getOrganizationId(),
                "APPOINTMENT", appointment == null ? self.patientId() : appointment.getId(), action, "PATIENT_SELF", Map.of("allowed", true));
    }
    private void changed(Self self, Appointment appointment, String action) {
        audit.change(self.personId(), appointment.getOrganizationId(), "APPOINTMENT", "Appointment", appointment.getId(),
                action, null, Map.of("status", appointment.getStatus(), "patientId", self.patientId()));
        accessed(self, appointment, action);
    }
    private record Self(UUID personId, UUID patientId) {}
    private record Busy(Instant start, Instant end) {}
    private record Window(Instant start, Instant end, LocalDate assignmentStart, LocalDate assignmentEnd) {
        boolean coversAssignment(Instant start, Instant end) {
            return !LocalDate.ofInstant(start, ZoneOffset.UTC).isBefore(assignmentStart)
                    && (assignmentEnd == null || !LocalDate.ofInstant(end, ZoneOffset.UTC).isAfter(assignmentEnd));
        }
    }
}
