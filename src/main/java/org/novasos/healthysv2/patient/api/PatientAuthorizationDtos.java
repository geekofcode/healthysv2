package org.novasos.healthysv2.patient.api;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class PatientAuthorizationDtos {
    private PatientAuthorizationDtos() {}
    public record CareRelationshipRequest(@NotNull UUID professionalId,@NotNull UUID organizationId,@NotBlank @Size(max=50) String relationshipType,Instant startDate,Instant endDate){@AssertTrue(message="{validation.care-relationship.dates}")public boolean isDatesValid(){return startDate==null||endDate==null||!endDate.isBefore(startDate);}}
    public record EndCareRelationshipRequest(Instant endedAt){}
    public record CareRelationshipResponse(UUID id,UUID patientId,UUID professionalId,UUID organizationId,String relationshipType,Instant startDate,Instant endDate,String status,Instant createdAt){}
    public record GrantConsentRequest(UUID granteePersonId,UUID granteeOrganizationId,@NotBlank @Size(max=255) String scope,@Size(max=255) String purpose,String reason,Instant grantedAt,Instant expiresAt){@AssertTrue(message="{validation.consent.grantee}")public boolean isGranteeValid(){return (granteePersonId==null)!=(granteeOrganizationId==null);}@AssertTrue(message="{validation.consent.expiration}")public boolean isExpirationValid(){return grantedAt==null||expiresAt==null||!expiresAt.isBefore(grantedAt);}}
    public record RevokeConsentRequest(String reason,Instant revokedAt){}
    public record ConsentResponse(UUID id,UUID patientId,UUID granteePersonId,UUID granteeOrganizationId,String scope,String purpose,String reason,Instant grantedAt,Instant expiresAt,Instant revokedAt,String status,Instant createdAt,Instant updatedAt,UUID createdBy,UUID updatedBy,long version){}
    public record AccessCheckResponse(UUID patientId,String scope,String action,boolean allowed,String decision){}
}
