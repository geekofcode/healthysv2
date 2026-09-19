package org.novasos.healthysv2.hospital;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface RoomRepository extends JpaRepository<Room, UUID> { Optional<Room> findByIdAndOrganizationId(UUID id, UUID organizationId); boolean existsByOrganizationIdAndRoomNumber(UUID organizationId, String roomNumber); }
