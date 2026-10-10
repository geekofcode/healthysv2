package org.novasos.healthysv2.hospital;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface OrganizationRepository extends JpaRepository<Organization, UUID> { boolean existsByNumber(String number);
 @org.springframework.data.jpa.repository.Query("select o from Organization o where (:scope is null or o.id=:scope) and (:query='' or lower(o.name) like lower(concat('%',:query,'%')) or lower(o.number) like lower(concat('%',:query,'%'))) and (:status='' or o.status=:status)")
 org.springframework.data.domain.Page<Organization> search(@org.springframework.data.repository.query.Param("scope") UUID scope, @org.springframework.data.repository.query.Param("query") String query, @org.springframework.data.repository.query.Param("status") String status, org.springframework.data.domain.Pageable pageable);
 org.springframework.data.domain.Page<Organization> findById(UUID id, org.springframework.data.domain.Pageable pageable); }
