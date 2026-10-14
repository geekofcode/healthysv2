package org.novasos.healthysv2.identity.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Omitted fields remain unchanged; an explicitly null avatar removes it. */
public final class UpdatePersonPreferencesRequest {
  private PersonPreferences.Theme theme;
  private PersonPreferences.Language language;

  @Pattern(regexp = "https://[^\\s]+", message = "Must be an HTTPS URL")
  @Size(max = 2048)
  private String avatarUrl;

  private boolean avatarProvided;

  @JsonSetter("theme")
  public void setTheme(PersonPreferences.Theme theme) {
    this.theme = theme;
  }

  @JsonSetter("language")
  public void setLanguage(PersonPreferences.Language language) {
    this.language = language;
  }

  @JsonSetter("avatarUrl")
  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
    this.avatarProvided = true;
  }

  public PersonPreferences.Theme theme() {
    return theme;
  }

  public PersonPreferences.Language language() {
    return language;
  }

  public String avatarUrl() {
    return avatarUrl;
  }

  public boolean avatarProvided() {
    return avatarProvided;
  }
}
