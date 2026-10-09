package org.novasos.healthysv2.professional;
import static org.novasos.healthysv2.professional.api.ProfessionalOnboardingDtos.*;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.novasos.healthysv2.shared.api.ApiPaths;

@RestController @RequestMapping(ApiPaths.V1+"/professional-onboarding")
@PreAuthorize("isAuthenticated()")
class ProfessionalOnboardingController {
    private final ProfessionalOnboardingService service;
    ProfessionalOnboardingController(ProfessionalOnboardingService service) {
        this.service=service;
    }

    @GetMapping("/directory") List<DirectoryEntry> directory() {
        return service.directory();
    }

    @GetMapping("/me") ResponseEntity<?> mine(JwtAuthenticationToken auth) {
        var dossier = service.mine(auth);
        return dossier == null
                ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null")
                : ResponseEntity.ok(dossier);
    }

    @GetMapping("/me/affiliations") List<MyAffiliationResponse> myAffiliations(JwtAuthenticationToken auth) {
        return service.myAffiliations(auth);
    }

    @PutMapping("/me") DossierResponse draft(JwtAuthenticationToken auth,@Valid @RequestBody DraftRequest input) {
        return service.draft(auth,input);
    }

    @PostMapping(value="/me/proof",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) DossierResponse proof(JwtAuthenticationToken auth,@RequestParam MultipartFile file) {
        return service.upload(auth,file);
    }

    @PostMapping("/me/submit") DossierResponse submit(JwtAuthenticationToken auth) {
        return service.submit(auth);
    }

    @GetMapping("/requests") @PreAuthorize("hasRole('PLATFORM_ADMIN')") List<DossierResponse> requests() {
        return service.requests();
    }

    @PostMapping("/requests/{id}/review") @PreAuthorize("hasRole('PLATFORM_ADMIN')") DossierResponse review(@PathVariable UUID id,@Valid @RequestBody ReviewRequest input) {
        return service.review(id,input);
    }

    @GetMapping("/requests/{id}/proof") ResponseEntity<byte[]> download(@PathVariable UUID id,JwtAuthenticationToken auth) {
        var proof=service.proof(id,auth);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(proof.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(proof.filename()).build().toString()).header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").body(proof.bytes());
    }

    @PostMapping("/invitations") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN')") InvitationResponse invite(@Valid @RequestBody InvitationRequest input) {
        return service.invite(input);
    }

    @GetMapping("/invitations") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN')") List<InvitationResponse> invitations() {
        return service.invitations();
    }

    @PostMapping("/invitations/accept") AffiliationResponse accept(JwtAuthenticationToken auth,@Valid @RequestBody AcceptanceRequest input) {
        return service.accept(auth,input);
    }

    @GetMapping("/affiliations") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN')") List<AffiliationResponse> affiliations() {
        return service.affiliations();
    }

    @DeleteMapping("/affiliations/{id}") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN')") ResponseEntity<Void> end(@PathVariable UUID id) {
        service.endAffiliation(id);
        return ResponseEntity.noContent().build();
    }
}
