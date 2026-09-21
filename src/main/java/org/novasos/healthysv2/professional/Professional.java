package org.novasos.healthysv2.professional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;
import org.novasos.healthysv2.shared.persistence.AuditableEntity;

@Entity
@Table(name="professional", schema="professional")
class Professional extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="person_id", nullable=false, unique=true) private UUID personId;
    @Column(name="professional_number", nullable=false, unique=true, length=50) private String number;
    @Column(name="professional_type", nullable=false, length=50) private String type;
    @Column(nullable=false, length=30) private String status="ACTIVE";
    @OneToMany(mappedBy="professional", cascade=CascadeType.ALL, orphanRemoval=true) private List<ProfessionalLicense> licenses=new ArrayList<>();
    @OneToMany(mappedBy="professional", cascade=CascadeType.ALL, orphanRemoval=true) private List<ProfessionalSpeciality> specialities=new ArrayList<>();
    @OneToMany(mappedBy="professional", cascade=CascadeType.ALL, orphanRemoval=true) private List<ProfessionalAssignment> assignments=new ArrayList<>();
    protected Professional() {}
    static Professional create(UUID personId,String number,String type,String status){var p=new Professional();p.personId=java.util.Objects.requireNonNull(personId);p.number=required(number);p.update(type,status);return p;}
    void update(String type,String status){this.type=required(type).toUpperCase();this.status=normalize(status,"ACTIVE");}
    ProfessionalLicense addLicense(String number,String authority,UUID countryId,java.time.LocalDate issuedAt,java.time.LocalDate expiresAt,String status){var child=ProfessionalLicense.create(this,number,authority,countryId,issuedAt,expiresAt,status);licenses.add(child);return child;}
    ProfessionalSpeciality addSpeciality(UUID catalogueId,boolean primary){if(primary)specialities.forEach(s->s.makeSecondary());var child=ProfessionalSpeciality.create(this,catalogueId,primary);specialities.add(child);return child;}
    ProfessionalAssignment addAssignment(UUID organizationId,UUID departmentId,UUID serviceId,String position,String employeeNumber,java.time.LocalDate startDate,java.time.LocalDate endDate,String status){var child=ProfessionalAssignment.create(this,organizationId,departmentId,serviceId,position,employeeNumber,startDate,endDate,status);assignments.add(child);return child;}
    void removeLicense(ProfessionalLicense child){licenses.remove(child);} void removeSpeciality(ProfessionalSpeciality child){specialities.remove(child);} void removeAssignment(ProfessionalAssignment child){assignments.remove(child);}
    UUID getId(){return id;} UUID getPersonId(){return personId;} String getNumber(){return number;} String getType(){return type;} String getStatus(){return status;} List<ProfessionalLicense> getLicenses(){return licenses;} List<ProfessionalSpeciality> getSpecialities(){return specialities;} List<ProfessionalAssignment> getAssignments(){return assignments;}
    static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Required value");return value.trim();}
    static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    static String normalize(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim().toUpperCase();}
}
