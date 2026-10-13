package org.novasos.healthysv2;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Adds only the role actually assigned in the selected establishment. */
@Component
class OrganizationAuthorities {
    private final JdbcTemplate jdbc;
    OrganizationAuthorities(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    Collection<GrantedAuthority> augment(Jwt jwt, Collection<GrantedAuthority> original) {
        UUID subject=uuid(jwt.getSubject());
        UUID organization=selectedOrganization(jwt);
        if (subject==null || organization==null) return original;
        var roles=jdbc.query("""
                select m.role from licensing.member m join identity.person p on p.id=m.person_id
                where p.keycloak_user_id=? and p.status='ACTIVE' and m.organization_id=? and m.status='ACTIVE'
                and (m.role not in ('DOCTOR','NURSE') or exists (
                    select 1 from professional.professional clinical join professional.professional_assignment a on a.professional_id=clinical.id
                    where clinical.person_id=p.id and clinical.status='ACTIVE' and a.organization_id=m.organization_id and a.status='ACTIVE'
                    and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date)
                    and (clinical.professional_type=m.role or (m.role='DOCTOR' and clinical.professional_type='MEDECIN'))
                    and not exists(select 1 from professional.registration_request r where r.person_id=p.id and r.status<>'APPROVED')
                ))
                union select 'HOSPITAL_ADMIN' from organization.organization o join identity.person p on p.id=o.administrator_person_id
                where p.keycloak_user_id=? and p.status='ACTIVE' and o.id=?
                """,(rs,n)->rs.getString(1),subject,organization,subject,organization);
        var result=new LinkedHashSet<GrantedAuthority>();
        var scoped=Set.of("ROLE_HOSPITAL_ADMIN","ROLE_HOSPITAL_AGENT","ROLE_HOSPITAL_VIEWER","ROLE_CASHIER","ROLE_ACCOUNTANT","ROLE_DOCTOR","ROLE_NURSE");
        original.stream().filter(a->!scoped.contains(a.getAuthority())).forEach(result::add);
        for (String role:roles) {
            if (Set.of("HOSPITAL_ADMIN","HOSPITAL_AGENT","CASHIER","ACCOUNTANT","DOCTOR","NURSE").contains(role)) result.add(new SimpleGrantedAuthority("ROLE_"+role));
        }
        return result;
    }

    private UUID selectedOrganization(Jwt jwt) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
            String selected=request.getRequest().getHeader("X-Organization-ID");
            if (selected!=null&&!selected.isBlank()) return uuid(selected);
        }
        UUID value=uuid(jwt.getClaimAsString("healthys_organization_id"));
        return value==null ? uuid(jwt.getClaimAsString("organization_id")) : value;
    }
    private UUID uuid(String value) { try { return value==null?null:UUID.fromString(value); } catch(IllegalArgumentException e) { return null; } }
}
