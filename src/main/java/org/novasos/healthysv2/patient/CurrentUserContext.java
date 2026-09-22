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
        UUID person=subject==null?null:jdbc.query("select id from identity.person where keycloak_user_id=? or id=? limit 1",rs->rs.next()?(UUID)rs.getObject(1):null,subject,subject);
        UUID organization=claimUuid(jwt,"healthys_organization_id");if(organization==null)organization=claimUuid(jwt,"organization_id");
        Set<String> roles=new HashSet<>();jwt.getAuthorities().forEach(a->{if(a.getAuthority().startsWith("ROLE_"))roles.add(a.getAuthority().substring(5));});
        return new UserContext(person,organization,Set.copyOf(roles));
    }
    private UUID claimUuid(JwtAuthenticationToken jwt,String name){Object value=jwt.getToken().getClaim(name);return value==null?null:uuid(value.toString());}
    private UUID uuid(String value){try{return value==null?null:UUID.fromString(value);}catch(IllegalArgumentException ignored){return null;}}
    public record UserContext(UUID personId,UUID organizationId,Set<String> roles){public boolean has(String role){return roles.contains(role);}public boolean hasAny(String...values){return Arrays.stream(values).anyMatch(roles::contains);}}
}
