package org.novasos.healthysv2.professional;
import java.time.LocalDate; import java.util.*; import jakarta.persistence.*;
@Entity @Table(name="professional_assignment",schema="professional")
class ProfessionalAssignment {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="professional_id") private Professional professional;
 @Column(name="organization_id",nullable=false) private UUID organizationId; @Column(name="department_id") private UUID departmentId; @Column(name="service_id") private UUID serviceId;
 @Column(length=150) private String position; @Column(name="employee_number",length=100) private String employeeNumber;
 @Column(name="start_date",nullable=false) private LocalDate startDate; @Column(name="end_date") private LocalDate endDate;
 @Column(nullable=false,length=30) private String status="ACTIVE";
 @OneToMany(mappedBy="assignment",cascade=CascadeType.ALL,orphanRemoval=true) private List<ProfessionalSchedule> schedules=new ArrayList<>();
 @OneToMany(mappedBy="assignment",cascade=CascadeType.ALL,orphanRemoval=true) private List<ProfessionalAvailability> availabilities=new ArrayList<>();
 protected ProfessionalAssignment(){}
 static ProfessionalAssignment create(Professional p,UUID o,UUID d,UUID s,String position,String employee,LocalDate start,LocalDate end,String status){var x=new ProfessionalAssignment();x.professional=p;x.update(o,d,s,position,employee,start,end,status);return x;}
 void update(UUID o,UUID d,UUID s,String p,String e,LocalDate start,LocalDate end,String status){if(s!=null&&d==null)throw new IllegalArgumentException("Service requires department");if(start==null)throw new IllegalArgumentException("Start date required");if(end!=null&&end.isBefore(start))throw new IllegalArgumentException("Invalid assignment dates");organizationId=Objects.requireNonNull(o);departmentId=d;serviceId=s;position=Professional.optional(p);employeeNumber=Professional.optional(e);startDate=start;endDate=end;this.status=Professional.normalize(status,"ACTIVE");}
 ProfessionalSchedule addSchedule(int day,java.time.LocalTime start,java.time.LocalTime end,int duration){var child=ProfessionalSchedule.create(this,day,start,end,duration);schedules.add(child);return child;}
 ProfessionalAvailability addAvailability(java.time.Instant start,java.time.Instant end,String type,String status){var child=ProfessionalAvailability.create(this,start,end,type,status);availabilities.add(child);return child;}
 void removeSchedule(ProfessionalSchedule child){schedules.remove(child);} void removeAvailability(ProfessionalAvailability child){availabilities.remove(child);}
 UUID getId(){return id;} UUID getOrganizationId(){return organizationId;} UUID getDepartmentId(){return departmentId;} UUID getServiceId(){return serviceId;} String getPosition(){return position;} String getEmployeeNumber(){return employeeNumber;} LocalDate getStartDate(){return startDate;} LocalDate getEndDate(){return endDate;} String getStatus(){return status;} List<ProfessionalSchedule> getSchedules(){return schedules;} List<ProfessionalAvailability> getAvailabilities(){return availabilities;}
}
