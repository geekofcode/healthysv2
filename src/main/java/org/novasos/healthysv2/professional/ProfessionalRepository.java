package org.novasos.healthysv2.professional;
import java.util.*; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
interface ProfessionalRepository extends JpaRepository<Professional,UUID> {
 boolean existsByPersonId(UUID personId); boolean existsByNumber(String number);
 @Query("select p from Professional p where :query='' or lower(p.number) like lower(concat('%',:query,'%')) or lower(p.type) like lower(concat('%',:query,'%')) or lower(p.status) like lower(concat('%',:query,'%'))")
 Page<Professional> search(@Param("query") String query,Pageable pageable);
}
