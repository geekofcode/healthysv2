package org.novasos.healthysv2;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reference values available before a professional role has been approved. */
@RestController
@PreAuthorize("isAuthenticated()")
class RegistrationReferenceController {
    private final JdbcTemplate jdbc;

    RegistrationReferenceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/registration-options")
    RegistrationOptions options() {
        var countries = jdbc.query("select id, iso2, name from shared.country order by name",
                (row, index) -> new Country(row.getObject("id", UUID.class),
                        row.getString("iso2"), row.getString("name")));
        var specialities = jdbc.query(
                "select id, code, name from catalog.speciality_catalog where active=true order by name",
                (row, index) -> new Speciality(row.getObject("id", UUID.class),
                        row.getString("code"), row.getString("name")));
        return new RegistrationOptions(countries, specialities);
    }

    record RegistrationOptions(List<Country> countries, List<Speciality> specialities) {}

    @GetMapping("/api/v1/professional-onboarding/me/professional")
    ResponseEntity<?> ownProfessional(JwtAuthenticationToken authentication) {
        UUID subject = UUID.fromString(authentication.getToken().getSubject());
        var rows = jdbc.query("""
                select p.id,p.professional_type from professional.professional p
                join identity.person person on person.id=p.person_id
                left join professional.registration_request r on r.professional_id=p.id
                where person.keycloak_user_id=? and person.status='ACTIVE' and p.status='ACTIVE'
                and (r.id is null or r.status='APPROVED')
                """, (row,index) -> new OwnProfessional(row.getObject("id", UUID.class),
                        row.getString("professional_type")), subject);
        return rows.isEmpty()
                ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null")
                : ResponseEntity.ok(rows.getFirst());
    }

    record OwnProfessional(UUID id, String professionalType) {}

    @PostMapping("/api/v1/admin/registration-options/countries")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    ResponseEntity<Country> addCountry(@Valid @RequestBody CountryRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into shared.country(id,iso2,name) values (?,?,?)
                on conflict (iso2) do nothing
                """, id, request.iso2(), request.name().trim());
        Country country = jdbc.queryForObject("select id,iso2,name from shared.country where iso2=?",
                (row,index) -> new Country(row.getObject("id", UUID.class),
                        row.getString("iso2"), row.getString("name")), request.iso2());
        return ResponseEntity.ok(country);
    }

    record CountryRequest(@NotBlank @Pattern(regexp="[A-Z]{2}") String iso2,
            @NotBlank @Size(max=150) String name) {}
    record Country(UUID id, String iso2, String name) {}
    record Speciality(UUID id, String code, String name) {}
}
