package org.novasos.healthysv2.hospital;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface CareServiceRepository extends JpaRepository<CareService, UUID> { Optional<CareService> findByIdAndDepartmentId(UUID id, UUID departmentId); boolean existsByDepartmentIdAndCode(UUID departmentId, String code); }
