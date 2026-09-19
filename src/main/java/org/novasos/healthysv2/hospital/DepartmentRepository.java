package org.novasos.healthysv2.hospital;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface DepartmentRepository extends JpaRepository<Department, UUID> { Optional<Department> findByIdAndOrganizationId(UUID id, UUID organizationId); boolean existsByOrganizationIdAndCode(UUID organizationId, String code); }
