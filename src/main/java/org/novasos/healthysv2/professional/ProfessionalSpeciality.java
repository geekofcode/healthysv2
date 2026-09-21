package org.novasos.healthysv2.professional;
import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="professional_speciality",schema="professional") @IdClass(ProfessionalSpecialityId.class)
class ProfessionalSpeciality {
 @Id @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="professional_id") private Professional professional;
 @Id @Column(name="speciality_catalog_id") private UUID specialityCatalogId;
 @Column(name="is_primary",nullable=false) private boolean primary; protected ProfessionalSpeciality(){}
 static ProfessionalSpeciality create(Professional p,UUID id,boolean primary){var x=new ProfessionalSpeciality();x.professional=p;x.specialityCatalogId=java.util.Objects.requireNonNull(id);x.primary=primary;return x;}
 void makeSecondary(){primary=false;} UUID getSpecialityCatalogId(){return specialityCatalogId;} boolean isPrimary(){return primary;}
}
