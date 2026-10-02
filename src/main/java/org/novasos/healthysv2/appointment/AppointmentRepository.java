package org.novasos.healthysv2.appointment;
import java.time.Instant;import java.util.*;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
interface AppointmentRepository extends JpaRepository<Appointment,UUID>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select a from Appointment a where a.id=:id") Optional<Appointment> findLockedById(@Param("id") UUID id);
 @Query("select a from Appointment a where a.patientId=:patient and ((:past=true and (a.end<:now or a.status in ('CANCELLED','COMPLETED','NO_SHOW'))) or (:past=false and a.end>=:now and a.status not in ('CANCELLED','COMPLETED','NO_SHOW')))")
 Page<Appointment> searchSelf(@Param("patient") UUID patient,@Param("past") boolean past,@Param("now") Instant now,Pageable pageable);
@Query("select a from Appointment a where (:patient is null or a.patientId=:patient) and (:professional is null or a.professionalId=:professional) and (:status is null or a.status=:status) and a.start>=:from and a.start<:to order by a.start")Page<Appointment> search(@Param("patient")UUID patient,@Param("professional")UUID professional,@Param("status")String status,@Param("from")Instant from,@Param("to")Instant to,Pageable pageable);}
