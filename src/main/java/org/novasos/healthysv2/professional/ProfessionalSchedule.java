package org.novasos.healthysv2.professional;
import java.time.LocalTime; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="professional_schedule",schema="professional")
class ProfessionalSchedule {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="assignment_id") private ProfessionalAssignment assignment;
 @Column(name="day_of_week",nullable=false) private short dayOfWeek; @Column(name="start_time",nullable=false) private LocalTime startTime; @Column(name="end_time",nullable=false) private LocalTime endTime; @Column(name="slot_duration_minutes",nullable=false) private int slotDurationMinutes;
 protected ProfessionalSchedule(){} static ProfessionalSchedule create(ProfessionalAssignment a,int d,LocalTime s,LocalTime e,int duration){var x=new ProfessionalSchedule();x.assignment=a;x.update(d,s,e,duration);return x;}
 void update(int d,LocalTime s,LocalTime e,int duration){if(d<1||d>7)throw new IllegalArgumentException("Invalid day");if(s==null||e==null||!e.isAfter(s))throw new IllegalArgumentException("Invalid schedule times");if(duration<=0)throw new IllegalArgumentException("Invalid slot duration");dayOfWeek=(short)d;startTime=s;endTime=e;slotDurationMinutes=duration;}
 UUID getId(){return id;} int getDayOfWeek(){return dayOfWeek;} LocalTime getStartTime(){return startTime;} LocalTime getEndTime(){return endTime;} int getSlotDurationMinutes(){return slotDurationMinutes;}
}
