package org.novasos.healthysv2.identity.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PersonPreferences(@NotNull Theme theme,
        @Pattern(regexp = "https://[^\\s]+", message = "Must be an HTTPS URL") @Size(max = 2048) String avatarUrl) {
    public enum Theme { LIGHT, DARK, SYSTEM }
}
