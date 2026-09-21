package org.novasos.healthysv2.professional;
import java.time.Instant; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="professional_availability",schema="professional")
class ProfessionalAvailability {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="assignment_id") private ProfessionalAssignment assignment;
 @Column(name="start_at",nullable=false) private Instant startAt; @Column(name="end_at",nullable=false) private Instant endAt;
 @Column(name="availability_type",nullable=false,length=30) private String type; @Column(nullable=false,length=30) private String status="AVAILABLE";
 protected ProfessionalAvailability(){} static ProfessionalAvailability create(ProfessionalAssignment a,Instant s,Instant e,String type,String status){var x=new ProfessionalAvailability();x.assignment=a;x.update(s,e,type,status);return x;}
 void update(Instant s,Instant e,String type,String status){if(s==null||e==null||!e.isAfter(s))throw new IllegalArgumentException("Invalid availability times");startAt=s;endAt=e;this.type=Professional.required(type).toUpperCase();this.status=Professional.normalize(status,"AVAILABLE");}
 UUID getId(){return id;} Instant getStartAt(){return startAt;} Instant getEndAt(){return endAt;} String getType(){return type;} String getStatus(){return status;}
}
