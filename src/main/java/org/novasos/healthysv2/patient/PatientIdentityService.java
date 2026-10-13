package org.novasos.healthysv2.patient;

import static org.novasos.healthysv2.patient.api.PatientIdentityDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.api.PatientDtos.IdentifierResponse;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class PatientIdentityService {
    private static final String IDENTITY_COLUMNS = """
            i.id person_id, i.person_number, i.first_name, i.last_name, i.birth_date, i.gender,
            (select c.value from identity.person_contact c where c.person_id=i.id and c.type='EMAIL'
                order by c.is_primary desc, c.id limit 1) email,
            (select c.value from identity.person_contact c where c.person_id=i.id and c.type in ('PHONE','MOBILE')
                order by c.is_primary desc, c.id limit 1) phone
            """;
    private static final String MATCH_IDENTITY = """
            (lower(i.first_name || ' ' || i.last_name) like ? escape '\\'
            or lower(i.last_name || ' ' || i.first_name) like ? escape '\\'
            or lower(i.person_number) like ? escape '\\'
            or exists(select 1 from identity.person_contact c where c.person_id=i.id
                and lower(c.value) like ? escape '\\'))
            """;
    private final JdbcTemplate jdbc;
    private final PatientRepository patients;
    private final CurrentUserContext users;
    private final LicensingAccess licensing;
    private final PatientAuditService audit;
    private final org.novasos.healthysv2.identity.api.ReceptionIdentityEditor editor;

    PatientIdentityService(JdbcTemplate jdbc, PatientRepository patients, CurrentUserContext users,
            LicensingAccess licensing, PatientAuditService audit, org.novasos.healthysv2.identity.api.ReceptionIdentityEditor editor) {
        this.editor = editor;
        this.jdbc = jdbc;
        this.patients = patients;
        this.users = users;
        this.licensing = licensing;
        this.audit = audit;
    }

    PageResponse<PatientIdentityResponse> search(String query, Pageable requested) {
        var user = requireIdentityReader();
        var page = boundedPage(requested);
        String term = query == null ? "" : query.trim();
        if (term.length() < 2 && !user.has("PLATFORM_ADMIN")) {
            return PageResponse.from(new PageImpl<>(List.of(), page, 0));
        }
        String pattern = pattern(term);
        String from = "from patient.patient p join identity.person i on i.id=p.person_id where ("
                + MATCH_IDENTITY + " or lower(p.patient_number) like ? escape '\\'"
                + " or exists(select 1 from patient.patient_identifier x where x.patient_id=p.id and lower(x.value) like ? escape '\\'))";
        Object[] args = {pattern, pattern, pattern, pattern, pattern, pattern};
        var result = searchPage("select p.id, p.patient_number, p.status, " + IDENTITY_COLUMNS,
                from, args, page, this::identity);
        audit.access(user.personId(), null, user.organizationId(), "PATIENT_IDENTITY_DIRECTORY", null,
                "SEARCH", "RECEPTION", Map.of("results", result.page().totalElements()));
        return result;
    }

    PageResponse<PersonIdentityResponse> searchPersons(String query, Pageable requested) {
        var user = requireIdentityReader();
        var page = boundedPage(requested);
        String term = query == null ? "" : query.trim();
        if (term.length() < 2 && !user.has("PLATFORM_ADMIN")) {
            return PageResponse.from(new PageImpl<>(List.of(), page, 0));
        }
        String pattern = pattern(term);
        String from = "from identity.person i left join patient.patient p on p.person_id=i.id where " + MATCH_IDENTITY;
        var result = searchPage("select p.id patient_id, p.patient_number, " + IDENTITY_COLUMNS,
                from, new Object[]{pattern, pattern, pattern, pattern}, page,
                (row, n) -> new PersonIdentityResponse(row.getObject("person_id", UUID.class),
                        row.getString("person_number"), row.getString("first_name"), row.getString("last_name"),
                        row.getObject("birth_date", java.time.LocalDate.class), row.getString("gender"),
                        row.getString("email"), row.getString("phone"), row.getObject("patient_id", UUID.class),
                        row.getString("patient_number")));
        audit.access(user.personId(), null, user.organizationId(), "PERSON_IDENTITY_DIRECTORY", null,
                "SEARCH", "RECEPTION", Map.of("results", result.page().totalElements()));
        return result;
    }

    PatientIdentityResponse find(UUID patientId) {
        var user = requireIdentityReader();
        var result = load(patientId);
        audit.access(user.personId(), patientId, user.organizationId(), "PATIENT_IDENTITY", patientId,
                "READ", "RECEPTION", Map.of());
        return result;
    }

    private PatientIdentityResponse load(UUID patientId) {
        var rows = jdbc.query("select p.id, p.patient_number, p.status, " + IDENTITY_COLUMNS
                + " from patient.patient p join identity.person i on i.id=p.person_id where p.id=?",
                this::identity, patientId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Patient", patientId);
        return rows.getFirst();
    }

    PatientIdentityResponse create(CreateIdentityRequest request) {
        var user = requireReception();
        // Serialize all creation paths for one person; the database unique key is the final guard.
        var persons = jdbc.query("select id from identity.person where id=? and status='ACTIVE' for update",
                (row, n) -> row.getObject(1, UUID.class), request.personId());
        if (persons.isEmpty()) throw new ResourceNotFoundException("Active person", request.personId());
        if (patients.existsByPersonId(request.personId())) {
            throw new ConflictException("PERSON_ALREADY_PATIENT", "error.patient.person.exists");
        }
        var patient = Patient.create(request.personId());
        for (var identifier : request.identifiers()) {
            Integer duplicate = jdbc.queryForObject("""
                    select count(*) from patient.patient_identifier
                    where upper(trim(type))=upper(trim(?)) and upper(trim(value))=upper(trim(?))
                    and coalesce(upper(trim(issuer)), '')=coalesce(upper(trim(?)), '')
                    """, Integer.class, identifier.type(), identifier.value(), identifier.issuer());
            if (duplicate != null && duplicate > 0) {
                throw new ConflictException("PATIENT_IDENTIFIER_ALREADY_EXISTS", "error.patient.identifier.exists");
            }
            patient.addIdentifier(identifier.type(), identifier.value(), identifier.issuer(),
                    identifier.countryId(), identifier.expirationDate());
        }
        patients.saveAndFlush(patient);
        audit.change(user.personId(), user.organizationId(), "PATIENT_IDENTITY", patient.getId(), "CREATE",
                Map.of("personId", request.personId()));
        return load(patient.getId());
    }

    PatientIdentityResponse update(UUID patientId, org.novasos.healthysv2.identity.api.ReceptionIdentityUpdate request) {
        var user = requireReception();
        var patient = patients.findById(patientId).orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        editor.updateReceptionIdentity(patient.getPersonId(), request);
        audit.change(user.personId(), user.organizationId(), "PATIENT_IDENTITY", patientId, "UPDATE", Map.of("personId", patient.getPersonId()));
        patients.flush();
        return find(patientId);
    }

    CurrentUserContext.UserContext requireReception() {
        var user = requireIdentityReader();
        if (!user.hasAny("PLATFORM_ADMIN", "HOSPITAL_ADMIN", "HOSPITAL_AGENT", "NURSE")) {
            throw new AccessDeniedException("RECEPTION_ROLE_REQUIRED");
        }
        return user;
    }

    private CurrentUserContext.UserContext requireIdentityReader() {
        var user = users.current();
        if (!user.hasAny("PLATFORM_ADMIN", "HOSPITAL_ADMIN", "HOSPITAL_AGENT", "NURSE", "DOCTOR")) {
            throw new AccessDeniedException("IDENTITY_READER_ROLE_REQUIRED");
        }
        if (user.organizationId() == null) {
            if (!user.has("PLATFORM_ADMIN")) throw new AccessDeniedException("ORGANIZATION_CONTEXT_REQUIRED");
        } else {
            licensing.requireMember(user.organizationId());
            licensing.requireActive(user.organizationId());
        }
        return user;
    }

    private PatientIdentityResponse identity(ResultSet row, int n) throws SQLException {
        UUID patient = row.getObject("id", UUID.class);
        var identifiers = jdbc.query("select * from patient.patient_identifier where patient_id=? order by type,value",
                (rs, index) -> new IdentifierResponse(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getString("value"), rs.getString("issuer"), rs.getObject("country_id", UUID.class),
                        rs.getObject("expiration_date", java.time.LocalDate.class)), patient);
        return new PatientIdentityResponse(patient, row.getObject("person_id", UUID.class),
                row.getString("patient_number"), row.getString("first_name"), row.getString("last_name"),
                row.getObject("birth_date", java.time.LocalDate.class), row.getString("gender"),
                row.getString("email"), row.getString("phone"), row.getString("status"), identifiers);
    }

    private <T> PageResponse<T> searchPage(String columns, String from, Object[] args,
            Pageable page, RowMapper<T> mapper) {
        Long total = jdbc.queryForObject("select count(*) " + from, Long.class, args);
        var pagedArgs = new java.util.ArrayList<>(java.util.Arrays.asList(args));
        pagedArgs.add(page.getPageSize());
        pagedArgs.add(page.getOffset());
        var rows = jdbc.query(columns + " " + from + " order by lower(i.last_name), lower(i.first_name), i.id limit ? offset ?",
                mapper, pagedArgs.toArray());
        return PageResponse.from(new PageImpl<>(rows, page, total == null ? 0 : total));
    }

    static Pageable boundedPage(Pageable requested) {
        return PageRequest.of(requested.isPaged() ? requested.getPageNumber() : 0,
                requested.isPaged() ? Math.min(100, requested.getPageSize()) : 20);
    }

    static String pattern(String query) {
        return "%" + query.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
