package org.novasos.healthysv2.professional;
import java.time.LocalDate; import java.util.UUID; import jakarta.persistence.*;
@Entity @Table(name="professional_license",schema="professional")
class ProfessionalLicense {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="professional_id") private Professional professional;
 @Column(name="license_number",nullable=false,length=100) private String number;
 @Column(name="issuing_authority") private String authority; @Column(name="country_id") private UUID countryId;
 @Column(name="issued_at") private LocalDate issuedAt; @Column(name="expires_at") private LocalDate expiresAt;
 @Column(nullable=false,length=30) private String status="ACTIVE"; protected ProfessionalLicense(){}
 static ProfessionalLicense create(Professional p,String n,String a,UUID c,LocalDate i,LocalDate e,String s){var x=new ProfessionalLicense();x.professional=p;x.update(n,a,c,i,e,s);return x;}
 void update(String n,String a,UUID c,LocalDate i,LocalDate e,String s){if(i!=null&&e!=null&&e.isBefore(i))throw new IllegalArgumentException("Invalid license dates");number=Professional.required(n);authority=Professional.optional(a);countryId=c;issuedAt=i;expiresAt=e;status=Professional.normalize(s,"ACTIVE");}
 UUID getId(){return id;} String getNumber(){return number;} String getAuthority(){return authority;} UUID getCountryId(){return countryId;} LocalDate getIssuedAt(){return issuedAt;} LocalDate getExpiresAt(){return expiresAt;} String getStatus(){return status;}
}
