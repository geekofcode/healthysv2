package org.novasos.healthysv2.consultation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="consultation",schema="consultation")
class Consultation {
 @Id private UUID id; @Column(name="consultation_number",nullable=false,unique=true,length=50) private String number;
 @Column(name="patient_id",nullable=false) private UUID patientId; @Column(name="professional_id",nullable=false) private UUID professionalId;
 @Column(name="organization_id",nullable=false) private UUID organizationId; @Column(name="appointment_id") private UUID appointmentId;
 @Column(name="encounter_id") private UUID encounterId; @Column(nullable=false,length=50) private String type;
 @Column(columnDefinition="text") private String reason; @Column(name="started_at",nullable=false) private Instant startedAt;
 @Column(name="completed_at") private Instant completedAt; @Column(nullable=false,length=30) private String status;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt; @Version private long version;
 @OneToMany(mappedBy="consultation",cascade=CascadeType.ALL,orphanRemoval=true) private List<VitalSign> vitalSigns=new ArrayList<>();
 @OneToMany(mappedBy="consultation",cascade=CascadeType.ALL,orphanRemoval=true) private List<Diagnosis> diagnoses=new ArrayList<>();
 @OneToMany(mappedBy="consultation",cascade=CascadeType.ALL,orphanRemoval=true) private List<ConsultationNote> notes=new ArrayList<>();
 @OneToMany(mappedBy="consultation",cascade=CascadeType.ALL,orphanRemoval=true) private List<ClinicalObservation> observations=new ArrayList<>();
 @OneToMany(mappedBy="consultation",cascade=CascadeType.ALL,orphanRemoval=true) private List<TreatmentPlan> treatments=new ArrayList<>();
 @OneToMany(mappedBy="consultation",cascade=CascadeType.ALL,orphanRemoval=true) private List<FollowUp> followUps=new ArrayList<>();
 protected Consultation(){}
 static Consultation start(UUID patient,UUID professional,UUID organization,UUID appointment,UUID encounter,String type,String reason){var x=new Consultation();x.id=UUID.randomUUID();x.number="CON-"+x.id.toString().replace("-","").substring(0,20).toUpperCase();x.patientId=Objects.requireNonNull(patient);x.professionalId=Objects.requireNonNull(professional);x.organizationId=Objects.requireNonNull(organization);x.appointmentId=appointment;x.encounterId=encounter;x.type=required(type).toUpperCase();x.reason=optional(reason);x.startedAt=Instant.now();x.createdAt=x.startedAt;x.updatedAt=x.startedAt;x.status="IN_PROGRESS";return x;}
 void complete(){ensureOpen();status="COMPLETED";completedAt=Instant.now();updatedAt=completedAt;}
 VitalSign addVitalSign(java.math.BigDecimal temperature,java.math.BigDecimal weight,java.math.BigDecimal height,Integer systolic,Integer diastolic,Integer heartRate,Integer respiratoryRate,java.math.BigDecimal oxygen,Instant measuredAt,UUID measuredBy){ensureOpen();var v=VitalSign.create(this,temperature,weight,height,systolic,diastolic,heartRate,respiratoryRate,oxygen,measuredAt,measuredBy);vitalSigns.add(v);touch();return v;}
 Diagnosis addDiagnosis(UUID catalog,String type,String description,String status){ensureOpen();var d=Diagnosis.create(this,catalog,type,description,status);diagnoses.add(d);touch();return d;}
 ConsultationNote addNote(UUID author,String type,String content){ensureOpen();var n=ConsultationNote.create(this,author,type,content);notes.add(n);touch();return n;}
 ClinicalObservation addObservation(UUID author,String type,String value,String notes,Instant observedAt){ensureOpen();var o=ClinicalObservation.create(this,author,type,value,notes,observedAt);observations.add(o);touch();return o;}
 TreatmentPlan addTreatment(String description,java.time.LocalDate start,java.time.LocalDate end,String status){ensureOpen();var t=TreatmentPlan.create(this,description,start,end,status);treatments.add(t);touch();return t;}
 FollowUp addFollowUp(java.time.LocalDate date,String instructions,String status){ensureOpen();var f=FollowUp.create(this,date,instructions,status);followUps.add(f);touch();return f;}
 private void ensureOpen(){if(!"IN_PROGRESS".equals(status))throw new IllegalStateException("Consultation is closed");}private void touch(){updatedAt=Instant.now();}
 private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Required value");return value.trim();}private static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
 UUID getId(){return id;}String getNumber(){return number;}UUID getPatientId(){return patientId;}UUID getProfessionalId(){return professionalId;}UUID getOrganizationId(){return organizationId;}UUID getAppointmentId(){return appointmentId;}UUID getEncounterId(){return encounterId;}String getType(){return type;}String getReason(){return reason;}Instant getStartedAt(){return startedAt;}Instant getCompletedAt(){return completedAt;}String getStatus(){return status;}Instant getCreatedAt(){return createdAt;}Instant getUpdatedAt(){return updatedAt;}long getVersion(){return version;}List<VitalSign> getVitalSigns(){return vitalSigns;}List<Diagnosis> getDiagnoses(){return diagnoses;}List<ConsultationNote> getNotes(){return notes;}List<ClinicalObservation> getObservations(){return observations;}List<TreatmentPlan> getTreatments(){return treatments;}List<FollowUp> getFollowUps(){return followUps;}
}
