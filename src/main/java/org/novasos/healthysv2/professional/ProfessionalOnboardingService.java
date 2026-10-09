package org.novasos.healthysv2.professional;
import static org.novasos.healthysv2.professional.api.ProfessionalOnboardingDtos.*;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;
import org.novasos.healthysv2.identity.api.IdentityProvisioningService;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.professional.api.ProfessionalRoleProvisioning;
import org.novasos.healthysv2.shared.api.error.*;
@Service @Transactional
class ProfessionalOnboardingService {
    private static final String DOSSIER_QUERY = """
            select id, keycloak_user_id, person_id,
                (select first_name from identity.person person where person.id=person_id) as first_name,
                (select last_name from identity.person person where person.id=person_id) as last_name,
                profession, license_number, issuing_authority, country_id, speciality_catalog_id,
                status, reason, professional_id, created_at, updated_at,
                (proof is not null) as proof_uploaded,
                (select status from professional.role_sync_outbox sync
                    where sync.subject_id=keycloak_user_id) as role_sync_status
            from professional.registration_request
            """;

    private final JdbcTemplate jdbc;
    private final IdentityProvisioningService identities;
    private final ProfessionalRepository professionals;
    private final CurrentUserContext users;
    private final AuditTrail audit;
    private final ProfessionalRoleProvisioning roles;
    ProfessionalOnboardingService(JdbcTemplate jdbc,IdentityProvisioningService identities,ProfessionalRepository professionals,CurrentUserContext users,AuditTrail audit,ProfessionalRoleProvisioning roles) {
        this.jdbc=jdbc;
        this.identities=identities;
        this.professionals=professionals;
        this.users=users;
        this.audit=audit;
        this.roles=roles;
    }
    DossierResponse mine(JwtAuthenticationToken auth) {
        return bySubject(subject(auth),false);
    }
    DossierResponse draft(JwtAuthenticationToken auth,DraftRequest input) {
        UUID subject=subject(auth);
        UUID person=provisionPerson(auth);
        lockSubject(subject);
        var old=bySubject(subject,false);
        if(old!=null&&!Set.of("DRAFT","REJECTED").contains(old.status()))throw conflict("This application cannot be edited");
        requireReference("shared.country",input.countryId());
        if(input.specialityCatalogId()!=null)requireReference("catalog.speciality_catalog",input.specialityCatalogId());
        saveDraft(subject,person,old,input);
        var result=bySubject(subject,false);
        change(result,"DRAFT",old);
        return result;
    }
    private UUID provisionPerson(JwtAuthenticationToken auth) {
        var jwt=auth.getToken();
        return identities.provisionIdentity(subject(auth), jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"), jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))).id();
    }

    private void saveDraft(UUID subject, UUID person, DossierResponse old, DraftRequest input) {
        if(old==null) {
            if(professionals.existsByPersonId(person))throw conflict("Professional already registered");
            jdbc.update("insert into professional.registration_request(id,keycloak_user_id,person_id,profession,license_number,issuing_authority,country_id,speciality_catalog_id) values (?,?,?,?,?,?,?,?)",UUID.randomUUID(),subject,person,input.profession(),input.licenseNumber().trim(),input.issuingAuthority().trim(),input.countryId(),input.specialityCatalogId());
        }
        else jdbc.update("update professional.registration_request set profession=?,license_number=?,issuing_authority=?,country_id=?,speciality_catalog_id=?,status='DRAFT',reason=null,updated_at=now() where id=?",input.profession(),input.licenseNumber().trim(),input.issuingAuthority().trim(),input.countryId(),input.specialityCatalogId(),old.id());
        if(old!=null && credentialsChanged(old,input)) {
            jdbc.update("update professional.registration_request set proof=null,proof_type=null,proof_name=null where id=?",old.id());
        }
    }

    private boolean credentialsChanged(DossierResponse old,DraftRequest input) {
        return !old.profession().equals(input.profession())
                || !old.licenseNumber().equals(input.licenseNumber().trim())
                || !old.issuingAuthority().equals(input.issuingAuthority().trim())
                || !old.countryId().equals(input.countryId());
    }

    DossierResponse upload(JwtAuthenticationToken auth,MultipartFile file) {
        var dossier=owned(auth,true);
        if(!Set.of("DRAFT","REJECTED").contains(dossier.status()))throw conflict("Application is locked");
        if(file.isEmpty()||file.getSize()>5242880)throw new IllegalArgumentException("Proof must be between 1 byte and 5 MB");
        try {
            byte[] bytes=file.getBytes();
            String type=proofType(bytes);
            jdbc.update("update professional.registration_request set proof=?,proof_type=?,proof_name=?,updated_at=now() where id=?",bytes,type,"professional-proof"+extension(type),dossier.id());
            return bySubject(subject(auth),false);
        }
        catch(java.io.IOException e) {
            throw new IllegalArgumentException("Unable to read proof",e);
        }
    }
    DossierResponse submit(JwtAuthenticationToken auth) {
        var dossier=owned(auth,true);
        if("SUBMITTED".equals(dossier.status()))return dossier;
        if(!Set.of("DRAFT","REJECTED").contains(dossier.status()))throw conflict("Invalid application transition");
        if(!dossier.proofUploaded())throw conflict("Proof is required before submitting");
        jdbc.update("update professional.registration_request set status='SUBMITTED',reason=null,updated_at=now() where id=?",dossier.id());
        var result=bySubject(subject(auth),false);
        change(result,"SUBMIT",dossier);
        return result;
    }
    List<DossierResponse> requests() {
        return jdbc.query(DOSSIER_QUERY+" order by updated_at desc limit 500",this::dossier);
    }
    DossierResponse review(UUID id,ReviewRequest input) {
        var before=byId(id,true);
        String status=reviewStatus(input.decision());
        if(status.equals(before.status()))return before;
        requireReviewTransition(before.status(),status);
        UUID professional=before.professionalId();
        if("APPROVED".equals(status)) {
            if(professional==null)professional=createProfessional(before);
            else {
                var p=professionals.findById(professional).orElseThrow();
                p.update(p.getType(),"ACTIVE");
            }
            roles.activate(before.keycloakUserId(),before.profession());
        }
        if("SUSPENDED".equals(status)) {
            var p=professionals.findById(professional).orElseThrow();
            p.update(p.getType(),"SUSPENDED");
            roles.deactivate(before.keycloakUserId(),before.profession());
        }
        jdbc.update("update professional.registration_request set status=?,reason=?,professional_id=?,updated_at=now() where id=?",status,input.reason().trim(),professional,id);
        var result=byId(id,false);
        change(result,input.decision(),before);
        return result;
    }
    private String reviewStatus(String decision) {
        return switch(decision) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            case "SUSPEND" -> "SUSPENDED";
            default -> throw new IllegalArgumentException("Invalid decision");
        };
    }

    private void requireReviewTransition(String from,String to) {
        boolean valid=switch(to) {
            case "APPROVED" -> Set.of("SUBMITTED","SUSPENDED").contains(from);
            case "REJECTED" -> "SUBMITTED".equals(from);
            case "SUSPENDED" -> "APPROVED".equals(from);
            default -> false;
        };
        if(!valid)throw conflict("Invalid professional review transition: "+from+" to "+to);
    }

    private UUID createProfessional(DossierResponse d) {
        lockSubject(d.keycloakUserId());
        if(professionals.existsByPersonId(d.personId()))throw conflict("Person already has a professional record");
        String type=switch(d.profession()) {
            case "medecin"->"DOCTOR";
            case "nurse"->"NURSE";
            case "laboratoire"->"LAB_TECHNICIAN";
            default->throw new IllegalArgumentException("Invalid profession");
        };
        if(Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from professional.professional_license where license_number=? and issuing_authority=?)",
                Boolean.class,d.licenseNumber(),d.issuingAuthority()))) {
            throw conflict("License is already linked to another professional");
        }
        var p=Professional.create(d.personId(),"PRO-"+d.id(),type,"ACTIVE");
        p.addLicense(d.licenseNumber(),d.issuingAuthority(),d.countryId(),null,null,"ACTIVE");
        if(d.specialityCatalogId()!=null)p.addSpeciality(d.specialityCatalogId(),true);
        return professionals.saveAndFlush(p).getId();
    }
    Proof proof(UUID id,JwtAuthenticationToken auth) {
        var d=byId(id,false);
        if(!users.current().has("PLATFORM_ADMIN")&&!d.keycloakUserId().equals(subject(auth)))throw new AccessDeniedException("Proof belongs to another applicant");
        audit.access(users.current().personId(),null,null,"professional_proof",id,"READ","Professional credential review",Map.of("ownerKeycloakUserId",d.keycloakUserId()));
        return jdbc.query("select proof,proof_type,proof_name from professional.registration_request where id=?",rs-> {
            if(!rs.next()||rs.getBytes(1)==null)throw new ResourceNotFoundException("Proof",id);
            return new Proof(rs.getBytes(1),rs.getString(2),rs.getString(3));
        },id);
    }
    List<DirectoryEntry> directory() {
        return jdbc.query("select p.id,p.person_id,person.first_name,person.last_name,r.profession from professional.professional p join professional.registration_request r on r.professional_id=p.id join identity.person person on person.id=p.person_id where r.status='APPROVED' and p.status='ACTIVE' and person.status='ACTIVE' order by person.last_name,person.first_name limit 500",(rs,n)->new DirectoryEntry(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5)));
    }
    InvitationResponse invite(InvitationRequest input) {
        requireOrganization(input.organizationId());
        requireReference("organization.organization",input.organizationId());
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
        UUID id=UUID.randomUUID();
        Instant expires=Instant.now().plus(Duration.ofDays(7));
        jdbc.update("insert into professional.organization_invitation(id,organization_id,email,position,token_hash,expires_at) values (?,?,?,?,?,?)",id,input.organizationId(),input.email().trim().toLowerCase(Locale.ROOT),input.position(),hash(token),java.sql.Timestamp.from(expires));
        audit.change(users.current().personId(),input.organizationId(),"professional","invitation",id,"INVITE",null,Map.of("email",input.email()));
        return new InvitationResponse(id,input.email().trim().toLowerCase(Locale.ROOT),input.organizationId(),input.position(),token,expires,"PENDING");
    }
    List<InvitationResponse> invitations() {
        UUID org=organizationScope();
        String sql="select * from professional.organization_invitation"+(org==null?"":" where organization_id=?")+" order by created_at desc limit 500";
        return jdbc.query(sql,(rs,n)->invitation(rs),org==null?new Object[0]:new Object[]{org}        );
    }
    AffiliationResponse accept(JwtAuthenticationToken auth,AcceptanceRequest input) {
        var jwt=auth.getToken();
        if(!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")))throw new AccessDeniedException("Verified email required");
        var invitation=lockedInvitation(input.token());
        String email=jwt.getClaimAsString("email");
        if(email==null||!email.equalsIgnoreCase(invitation.email()))throw new AccessDeniedException("Invitation email does not match authenticated identity");
        var dossier=owned(auth,true);
        if(!"APPROVED".equals(dossier.status())||dossier.professionalId()==null)throw conflict("Professional verification required before affiliation");
        var p=professionals.findById(dossier.professionalId()).orElseThrow();
        if(!"ACTIVE".equals(p.getStatus()))throw conflict("Professional is suspended");
        if("ACCEPTED".equals(invitation.status())&&dossier.personId().equals(invitation.acceptedBy()))return activeAffiliation(p,invitation.organizationId());
        if(!"PENDING".equals(invitation.status())||invitation.expiresAt().isBefore(Instant.now()))throw conflict("Invitation is expired or no longer available");
        var existing=p.getAssignments().stream().filter(a->a.getOrganizationId().equals(invitation.organizationId())&&"ACTIVE".equals(a.getStatus())).findFirst();
        ProfessionalAssignment assignment=existing.orElseGet(()->p.addAssignment(invitation.organizationId(),null,null,invitation.position(),null,LocalDate.now(),null,"ACTIVE"));
        professionals.flush();
        jdbc.update("update professional.organization_invitation set status='ACCEPTED',accepted_by=? where id=?",dossier.personId(),invitation.id());
        audit.change(dossier.personId(),invitation.organizationId(),"professional","assignment",assignment.getId(),"ACCEPT",null,Map.of("professionalId",p.getId()));
        return affiliation(p.getId(),assignment);
    }
    private Invitation lockedInvitation(String token) {
        var rows=jdbc.query("select * from professional.organization_invitation where token_hash=? for update",
                (rs,n)->new Invitation(rs.getObject("id",UUID.class),rs.getObject("organization_id",UUID.class),
                        rs.getString("email"),rs.getString("position"),rs.getString("status"),
                        rs.getTimestamp("expires_at").toInstant(),rs.getObject("accepted_by",UUID.class)),hash(token));
        if(rows.isEmpty())throw new ResourceNotFoundException("Invitation","token");
        return rows.getFirst();
    }

    private record Invitation(UUID id,UUID organizationId,String email,String position,
            String status,Instant expiresAt,UUID acceptedBy) {}

    List<MyAffiliationResponse> myAffiliations(JwtAuthenticationToken auth) {
        UUID subject=subject(auth);
        return jdbc.query("select a.id,a.professional_id,a.organization_id,a.status,o.name from professional.professional_assignment a join professional.professional p on p.id=a.professional_id join identity.person person on person.id=p.person_id join organization.organization o on o.id=a.organization_id where person.keycloak_user_id=? and a.status='ACTIVE' and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date) and p.status='ACTIVE' order by o.name",(rs,n)->new MyAffiliationResponse(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5)),subject);
    }
    List<AffiliationResponse> affiliations() {
        UUID org=organizationScope();
        return jdbc.query("select id,professional_id,organization_id,status from professional.professional_assignment"+(org==null?"":" where organization_id=?")+" order by start_date desc limit 500",(rs,n)->new AffiliationResponse(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getString(4)),org==null?new Object[0]:new Object[]{org}        );
    }
    void endAffiliation(UUID id) {
        var rows=jdbc.query("select professional_id,organization_id from professional.professional_assignment where id=? for update",(rs,n)->new UUID[] {
            rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)
        },id);
        if(rows.isEmpty())throw new ResourceNotFoundException("Affiliation",id);
        UUID org=rows.getFirst()[1];
        requireOrganization(org);
        jdbc.update("update professional.professional_assignment set status='ENDED',end_date=greatest(start_date,current_date) where id=?",id);
        audit.change(users.current().personId(),org,"professional","assignment",id,"END",null,Map.of("status","ENDED"));
    }
    private AffiliationResponse activeAffiliation(Professional p,UUID org) {
        return p.getAssignments().stream().filter(a->org.equals(a.getOrganizationId())&&"ACTIVE".equals(a.getStatus())).findFirst().map(a->affiliation(p.getId(),a)).orElseThrow(()->conflict("Affiliation has ended"));
    }
    private AffiliationResponse affiliation(UUID p,ProfessionalAssignment a) {
        return new AffiliationResponse(a.getId(),p,a.getOrganizationId(),a.getStatus());
    }
    private InvitationResponse invitation(ResultSet rs)throws SQLException {
        return new InvitationResponse(rs.getObject("id",UUID.class),rs.getString("email"),rs.getObject("organization_id",UUID.class),rs.getString("position"),null,rs.getTimestamp("expires_at").toInstant(),rs.getString("status"));
    }
    private UUID organizationScope() {
        var u=users.current();
        if(u.has("PLATFORM_ADMIN"))return null;
        if(!u.has("HOSPITAL_ADMIN")||u.organizationId()==null)throw new AccessDeniedException("Organization administrator scope required");
        return u.organizationId();
    }
    private void requireOrganization(UUID org) {
        UUID scope=organizationScope();
        if(scope!=null&&!scope.equals(org))throw new AccessDeniedException("Organization outside administrator scope");
    }
    private UUID subject(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        }
        catch(IllegalArgumentException e) {
            throw new AccessDeniedException("Valid identity required");
        }
    }
    private DossierResponse owned(JwtAuthenticationToken auth,boolean lock) {
        var d=bySubject(subject(auth),lock);
        if(d==null)throw new ResourceNotFoundException("ProfessionalApplication",subject(auth));
        return d;
    }
    private DossierResponse bySubject(UUID id,boolean lock) {
        var rows=jdbc.query(DOSSIER_QUERY+" where keycloak_user_id=?"+(lock?" for update":""),this::dossier,id);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private DossierResponse byId(UUID id,boolean lock) {
        var rows=jdbc.query(DOSSIER_QUERY+" where id=?"+(lock?" for update":""),this::dossier,id);
        if(rows.isEmpty())throw new ResourceNotFoundException("ProfessionalApplication",id);
        return rows.getFirst();
    }
    private DossierResponse dossier(ResultSet rs,int n)throws SQLException {
        return new DossierResponse(rs.getObject("id",UUID.class),rs.getObject("person_id",UUID.class),rs.getObject("keycloak_user_id",UUID.class),rs.getString("first_name"),rs.getString("last_name"),rs.getString("profession"),rs.getString("license_number"),rs.getString("issuing_authority"),rs.getObject("country_id",UUID.class),rs.getObject("speciality_catalog_id",UUID.class),rs.getString("status"),rs.getString("reason"),rs.getString("role_sync_status"),rs.getBoolean("proof_uploaded"),rs.getObject("professional_id",UUID.class),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
    }
    private void lockSubject(UUID id) {
        jdbc.execute("select pg_advisory_xact_lock("+(id.getMostSignificantBits()^id.getLeastSignificantBits())+")");
    }
    private void requireReference(String table,UUID id) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from "+table+" where id=?)",Boolean.class,id)))throw new ResourceNotFoundException(table,id);
    }
    private void change(DossierResponse d,String action,DossierResponse before) {
        var details=new LinkedHashMap<String,Object>();
        details.put("status",d.status());
        if(d.reason()!=null)details.put("reason",d.reason());
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth instanceof JwtAuthenticationToken jwt)details.put("reviewerKeycloakUserId",jwt.getToken().getSubject());
        audit.change(users.current().personId(),null,"professional","registration_request",d.id(),action,before==null?null:Map.of("status",before.status()),details);
    }
    private ConflictException conflict(String message) {
        return new ConflictException("PROFESSIONAL_ONBOARDING_CONFLICT",message);
    }
    private byte[] randomBytes() {
        byte[] value=new byte[32];
        new SecureRandom().nextBytes(value);
        return value;
    }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    private String proofType(byte[] bytes) {
        if(bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))return "application/pdf";
        if(bytes.length>=8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71&&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10)return "image/png";
        if(bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255)return "image/jpeg";
        throw new IllegalArgumentException("Only PDF, PNG or JPEG proofs are accepted");
    }
    private String extension(String type) {
        return switch(type) {
            case "application/pdf"->".pdf";
            case "image/png"->".png";
            default->".jpg";
        };
    }
}
