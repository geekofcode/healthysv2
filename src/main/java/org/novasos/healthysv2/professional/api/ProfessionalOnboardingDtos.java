package org.novasos.healthysv2.professional.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class ProfessionalOnboardingDtos {
    private ProfessionalOnboardingDtos() {}

    public record DraftRequest(
            @NotBlank @Pattern(regexp="medecin|nurse|laboratoire") String profession,
            @NotBlank @Size(max=100) String licenseNumber,
            @NotBlank @Size(max=255) String issuingAuthority,
            @NotNull UUID countryId, UUID specialityCatalogId,
            @Size(max=255) String specialityName,
            @Pattern(regexp="PASSPORT|NATIONAL_ID|DRIVING_LICENSE") String identityDocumentType,
            LocalDate identityExpiresOn) {}

    public record ReviewRequest(
            @NotBlank @Pattern(regexp="APPROVE|REJECT|SUSPEND") String decision,
            @NotBlank @Size(max=2000) String reason) {}

    public record InvitationRequest(@NotBlank @Email @Size(max=254) String email,
            @NotNull UUID organizationId, @Size(max=150) String position) {}

    public record AcceptanceRequest(@NotBlank @Size(max=100) String token) {}

    public record DossierResponse(UUID id, UUID personId, UUID keycloakUserId,
            String firstName, String lastName, String profession, String licenseNumber,
            String issuingAuthority, UUID countryId, UUID specialityCatalogId,
            String status, String reason, String roleSyncStatus, boolean proofUploaded,
            String specialityName, String identityDocumentType, LocalDate identityExpiresOn,
            boolean identityFrontUploaded, boolean identityBackUploaded,
            UUID professionalId, Instant createdAt, Instant updatedAt) {}

    public record InvitationResponse(UUID id, String email, UUID organizationId,
            String position, String token, Instant expiresAt, String status) {}

    public record MyAffiliationResponse(UUID id, UUID professionalId, UUID organizationId,
            String status, String organizationName) {}

    public record AffiliationResponse(UUID id, UUID professionalId, UUID organizationId,
            String status) {}

    public record DirectoryEntry(UUID professionalId, UUID personId, String firstName,
            String lastName, String profession) {}

    public record Proof(byte[] bytes, String contentType, String filename) {}
}
