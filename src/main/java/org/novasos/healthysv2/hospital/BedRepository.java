package org.novasos.healthysv2.hospital;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface BedRepository extends JpaRepository<Bed, UUID> { Optional<Bed> findByIdAndRoomId(UUID id, UUID roomId); boolean existsByRoomIdAndBedNumber(UUID roomId, String bedNumber); }
