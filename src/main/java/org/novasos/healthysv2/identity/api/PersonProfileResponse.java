package org.novasos.healthysv2.identity.api;

import java.util.List;
import java.util.UUID;

public record PersonProfileResponse(PersonResponse person,
        UpdatePersonProfileRequest.HomeAddress homeAddress,
        List<LanguageOption> languages, List<CountryOption> countries) {
    public record LanguageOption(UUID id, String code, String label) {}
    public record CountryOption(UUID id, String iso2, String name) {}
}
