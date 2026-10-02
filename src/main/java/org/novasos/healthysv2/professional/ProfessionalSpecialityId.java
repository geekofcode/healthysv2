package org.novasos.healthysv2.professional;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ProfessionalSpecialityId implements Serializable {
    private UUID professional;
    private UUID specialityCatalogId;
    public ProfessionalSpecialityId() {}
    public ProfessionalSpecialityId(UUID professional, UUID specialityCatalogId) {
        this.professional = professional; this.specialityCatalogId = specialityCatalogId;
    }
    @Override public boolean equals(Object other) {
        return other instanceof ProfessionalSpecialityId id
                && Objects.equals(professional, id.professional)
                && Objects.equals(specialityCatalogId, id.specialityCatalogId);
    }
    @Override public int hashCode() { return Objects.hash(professional, specialityCatalogId); }
}
