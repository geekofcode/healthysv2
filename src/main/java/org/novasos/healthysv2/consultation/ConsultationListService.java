package org.novasos.healthysv2.consultation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.consultation.api.ConsultationDtos.ConsultationSummary;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
class ConsultationListService {
    private final JdbcTemplate jdbc;
    private final CurrentUserContext users;
    private final PatientAccessService access;

    ConsultationListService(JdbcTemplate jdbc, CurrentUserContext users, PatientAccessService access) {
        this.jdbc=jdbc; this.users=users; this.access=access;
    }

    PageResponse<ConsultationSummary> list(String query, String status, UUID patient, int page, int size) {
        if(page<0 || size<1 || size>100) throw new BusinessRuleException("INVALID_PAGE","error.malformed");
        var user=users.current();
        if(!user.hasAny("PLATFORM_ADMIN","HOSPITAL_ADMIN","DOCTOR","NURSE")) throw new org.springframework.security.access.AccessDeniedException("Consultation list role denied");
        var parameters=new ArrayList<Object>();
        StringBuilder where=new StringBuilder(" where 1=1");
        if (!user.has("PLATFORM_ADMIN")) {
            where.append(" and c.organization_id is not distinct from cast(? as uuid)");
            parameters.add(user.organizationId());
            if (user.has("HOSPITAL_ADMIN")) {
                where.append(" and exists(select 1 from patient.patient_registration r where r.patient_id=p.id and r.organization_id=? and r.status='ACTIVE')");
                parameters.add(user.organizationId());
            } else {
                where.append(CLINICAL_SCOPE);
                parameters.add(user.personId());
                parameters.add(user.organizationId());
                parameters.add(user.organizationId());
                parameters.add(user.organizationId());
                parameters.add(user.organizationId());
                parameters.add(user.personId());
                parameters.add(user.organizationId());
                parameters.add(user.organizationId());
                parameters.add(user.organizationId());
                parameters.add(user.personId());
            }
        }
        if(patient!=null) { access.requireAccess(patient,"MEDICAL_RECORD","READ"); where.append(" and c.patient_id=?"); parameters.add(patient); }
        if(status!=null&&!status.isBlank()) {where.append(" and c.status=?");parameters.add(status.trim().toUpperCase(java.util.Locale.ROOT));}
        if(query!=null&&!query.isBlank()) {
            where.append(" and (lower(c.consultation_number) like ? or lower(p.patient_number) like ? or lower(concat_ws(' ',i.first_name,i.last_name)) like ?)");
            String search="%"+query.trim().toLowerCase(java.util.Locale.ROOT)+"%";
            parameters.add(search);parameters.add(search);parameters.add(search);
        }
        String from=" from consultation.consultation c join patient.patient p on p.id=c.patient_id join identity.person i on i.id=p.person_id";
        Long total=jdbc.queryForObject("select count(*)"+from+where,Long.class,parameters.toArray());
        var pageParameters=new ArrayList<>(parameters);pageParameters.add(size);pageParameters.add((long)page*size);
        List<ConsultationSummary> content=jdbc.query("select c.id,c.consultation_number,c.patient_id,c.professional_id,c.organization_id,c.type,c.started_at,c.status"+from+where+" order by c.started_at desc,c.id limit ? offset ?",
                (rs,n)->new ConsultationSummary(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getObject(5,UUID.class),rs.getString(6),rs.getTimestamp(7).toInstant(),rs.getString(8)),pageParameters.toArray());
        content.stream().map(ConsultationSummary::patientId).distinct().forEach(id->access.requireAccess(id,"MEDICAL_RECORD","READ"));
        return PageResponse.from(new PageImpl<>(content,PageRequest.of(page,size),total==null?0:total));
    }

    private static final String CLINICAL_SCOPE="""
        and exists(select 1 from professional.professional pro
            join patient.care_relationship cr on cr.professional_id=pro.id
            where pro.person_id=? and pro.status='ACTIVE' and cr.patient_id=p.id
            and cr.organization_id is not distinct from cast(? as uuid)
            and cr.status='ACTIVE' and cr.start_date<=clock_timestamp()
            and (cr.end_date is null or cr.end_date>clock_timestamp())
            and (cast(? as uuid) is null or (exists(select 1 from professional.professional_assignment a
                where a.professional_id=pro.id and a.organization_id=? and a.status='ACTIVE'
                and a.start_date<=current_date and (a.end_date is null or a.end_date>=current_date))
                and exists(select 1 from patient.patient_registration r where r.patient_id=p.id
                    and r.organization_id=? and r.status='ACTIVE')))
            and exists(select 1 from patient.consent consent where consent.patient_id=p.id
                and consent.status='ACTIVE' and consent.revoked_at is null and consent.granted_at<=clock_timestamp()
                and (consent.expires_at is null or consent.expires_at>clock_timestamp())
                and consent.scope in ('MEDICAL_RECORD','FULL_RECORD')
                and (consent.grantee_person_id=? or (cast(? as uuid) is not null and consent.grantee_organization_id=?)))
            and (cast(? as uuid) is not null or exists(select 1 from professional.professional owner
                where owner.id=c.professional_id and owner.person_id=? and owner.status='ACTIVE')))
        """;
}
