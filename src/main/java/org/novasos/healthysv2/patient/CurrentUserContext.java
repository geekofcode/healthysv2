package org.novasos.healthysv2.patient;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserContext {
    private final JdbcTemplate jdbc;
    CurrentUserContext(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public UserContext current(){
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof JwtAuthenticationToken jwt))return new UserContext(null,null,Set.of());
        UUID subject=uuid(jwt.getToken().getSubject());
        UUID person=subject==null?null:jdbc.query("select id from identity.person where keycloak_user_id=? limit 1",rs->rs.next()?(UUID)rs.getObject(1):null,subject);
        UUID organization=claimUuid(jwt,"healthys_organization_id");if(organization==null)organization=claimUuid(jwt,"organization_id");
        Set<String> roles=new HashSet<>();jwt.getAuthorities().forEach(a->{if(a.getAuthority().startsWith("ROLE_"))roles.add(a.getAuthority().substring(5));});
        UUID claimedOrganization = organization;
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            String selected = servlet.getRequest().getHeader("X-Organization-ID");
            if (selected != null && !selected.isBlank()) {
                if ("independent".equalsIgnoreCase(selected.trim())) {
                    if (roles.stream().noneMatch(Set.of("DOCTOR", "NURSE", "PHARMACIST", "LAB_TECHNICIAN")::contains)
                            || roles.stream().anyMatch(Set.of("HOSPITAL_ADMIN", "HOSPITAL_VIEWER", "HOSPITAL_AGENT")::contains)) {
                        throw new org.springframework.security.access.AccessDeniedException("INDEPENDENT_CONTEXT_DENIED");
                    }
                    organization = null;
                } else {
                    organization = uuid(selected);
                    if (organization == null) throw new org.springframework.security.access.AccessDeniedException("INVALID_ORGANIZATION_CONTEXT");
                }
            }
        }
        if (organization != null && !roles.contains("PLATFORM_ADMIN")) {
            if (roles.stream().anyMatch(Set.of("DOCTOR", "NURSE", "PHARMACIST", "LAB_TECHNICIAN")::contains)) {
                Integer active = person == null ? 0 : jdbc.queryForObject("""
                        select count(*) from professional.professional p
                        join professional.professional_assignment a on a.professional_id=p.id
                        where p.person_id=? and p.status='ACTIVE' and a.organization_id=?
                        and a.status='ACTIVE' and a.start_date<=current_date
                        and (a.end_date is null or a.end_date>=current_date)
                        """, Integer.class, person, organization);
                if (active == null || active == 0) throw new org.springframework.security.access.AccessDeniedException("PROFESSIONAL_NOT_ASSIGNED");
            } else if (!organization.equals(claimedOrganization)) {
                throw new org.springframework.security.access.AccessDeniedException("ORGANIZATION_CONTEXT_DENIED");
            }
        }
        return new UserContext(person,organization,Set.copyOf(roles));
    }
    private UUID claimUuid(JwtAuthenticationToken jwt,String name){Object value=jwt.getToken().getClaim(name);return value==null?null:uuid(value.toString());}
    private UUID uuid(String value){try{return value==null?null:UUID.fromString(value);}catch(IllegalArgumentException ignored){return null;}}
    public record UserContext(UUID personId,UUID organizationId,Set<String> roles){public boolean has(String role){return roles.contains(role);}public boolean hasAny(String...values){return Arrays.stream(values).anyMatch(roles::contains);}}
}
