package org.novasos.healthysv2.identity.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PersonPreferences(
    @NotNull Theme theme,
    @Pattern(regexp = "https://[^\\s]+", message = "Must be an HTTPS URL") @Size(max = 2048)
        String avatarUrl,
    @NotNull Language language) {
  public PersonPreferences(Theme theme, String avatarUrl) {
    this(theme, avatarUrl, Language.SYSTEM);
  }

  public enum Theme {
    LIGHT,
    DARK,
    SYSTEM
  }

  public enum Language {
    EN,
    FR,
    SYSTEM
  }
}
