package org.novasos.healthysv2.document;
import java.util.*;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
interface HealthDocumentRepository extends JpaRepository<HealthDocument,UUID>{
 @Query("select d from HealthDocument d where (:patientId is null or d.patientId=:patientId) and (:status is null or d.status=:status) and (:categoryId is null or d.categoryId=:categoryId)")
 Page<HealthDocument> search(@Param("patientId")UUID patientId,@Param("categoryId")UUID categoryId,@Param("status")String status,Pageable pageable);
}
