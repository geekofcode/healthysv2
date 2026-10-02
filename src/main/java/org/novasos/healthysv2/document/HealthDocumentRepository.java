package org.novasos.healthysv2.document;
import java.util.*;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
interface HealthDocumentRepository extends JpaRepository<HealthDocument,UUID>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select d from HealthDocument d where d.id=:id") Optional<HealthDocument> findLockedById(@Param("id")UUID id);
 @Query(value="select d.* from document.document d where d.owner_patient_id=:patient and d.patient_visible=true and d.status='ACTIVE' and (cast(:category as uuid) is null or d.category_id=:category) and (cast(:consultation as uuid) is null or exists(select 1 from document.document_link l where l.document_id=d.id and l.resource_type='CONSULTATION' and l.resource_id=:consultation)) order by d.uploaded_at desc,d.id desc",countQuery="select count(*) from document.document d where d.owner_patient_id=:patient and d.patient_visible=true and d.status='ACTIVE' and (cast(:category as uuid) is null or d.category_id=:category) and (cast(:consultation as uuid) is null or exists(select 1 from document.document_link l where l.document_id=d.id and l.resource_type='CONSULTATION' and l.resource_id=:consultation))",nativeQuery=true)
 Page<HealthDocument> searchVisible(@Param("patient")UUID patient,@Param("category")UUID category,@Param("consultation")UUID consultation,Pageable pageable);
 @Query("select d from HealthDocument d where (:patientId is null or d.patientId=:patientId) and (:status is null or d.status=:status) and (:categoryId is null or d.categoryId=:categoryId)")
 Page<HealthDocument> search(@Param("patientId")UUID patientId,@Param("categoryId")UUID categoryId,@Param("status")String status,Pageable pageable);
}
