package org.novasos.healthysv2.patient;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;

interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByPersonId(UUID personId);
    boolean existsByPersonId(UUID personId);
    @Query("select p from Patient p where :query='' or lower(p.patientNumber) like lower(concat('%',:query,'%')) or lower(p.status) like lower(concat('%',:query,'%')) or lower(coalesce(p.bloodGroup,'')) like lower(concat('%',:query,'%')) or lower(coalesce(p.occupation,'')) like lower(concat('%',:query,'%'))")
    Page<Patient> search(@Param("query") String query, Pageable pageable);
    String SCOPED_PATIENTS = """
            from patient.patient p where
            (:query='' or lower(p.patient_number) like lower(concat('%',:query,'%'))
            or lower(p.status) like lower(concat('%',:query,'%'))
            or lower(coalesce(p.blood_group,'')) like lower(concat('%',:query,'%'))
            or lower(coalesce(p.occupation,'')) like lower(concat('%',:query,'%')))
            and ((:clinical=false and exists(select 1 from patient.patient_registration r
                where r.patient_id=p.id and r.organization_id=:organization and r.status='ACTIVE'))
            or (:clinical=true and exists(select 1 from professional.professional pro
                join patient.care_relationship cr on cr.professional_id=pro.id
                where pro.person_id=:person and pro.status='ACTIVE' and cr.patient_id=p.id
                and cr.organization_id is not distinct from cast(:organization as uuid)
                and cr.status='ACTIVE' and cr.start_date<=clock_timestamp()
                and (cr.end_date is null or cr.end_date>clock_timestamp())
                and (cast(:organization as uuid) is null or (exists(select 1 from professional.professional_assignment a
                    where a.professional_id=pro.id and a.organization_id=:organization
                    and a.status='ACTIVE' and a.start_date<=current_date
                    and (a.end_date is null or a.end_date>=current_date))
                    and exists(select 1 from patient.patient_registration r where r.patient_id=p.id
                        and r.organization_id=:organization and r.status='ACTIVE')))
                and exists(select 1 from patient.consent c where c.patient_id=p.id
                    and c.status='ACTIVE' and c.revoked_at is null and c.granted_at<=clock_timestamp()
                    and (c.expires_at is null or c.expires_at>clock_timestamp())
                    and c.scope in ('MEDICAL_RECORD','FULL_RECORD')
                    and (c.grantee_person_id=:person or (cast(:organization as uuid) is not null
                        and c.grantee_organization_id=:organization))))))
            """;
    @Query(value="select p.* " + SCOPED_PATIENTS,
            countQuery="select count(*) " + SCOPED_PATIENTS, nativeQuery=true)
    Page<Patient> searchScoped(@Param("query") String query, @Param("organization") UUID organization,
            @Param("person") UUID person, @Param("clinical") boolean clinical, Pageable pageable);
}
