package org.novasos.healthysv2.hospital;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface OrganizationRepository extends JpaRepository<Organization, UUID> { boolean existsByNumber(String number); }
