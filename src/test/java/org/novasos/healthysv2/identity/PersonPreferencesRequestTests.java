package org.novasos.healthysv2.identity;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.identity.api.PersonPreferences;
import org.novasos.healthysv2.identity.api.UpdatePersonPreferencesRequest;

class PersonPreferencesRequestTests {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void languageOnlyUpdateDoesNotSpecifyThemeOrAvatar() throws Exception {
    var request = mapper.readValue("{\"language\":\"FR\"}", UpdatePersonPreferencesRequest.class);
    assertEquals(PersonPreferences.Language.FR, request.language());
    assertNull(request.theme());
    assertFalse(request.avatarProvided());
  }

  @Test
  void explicitNullAvatarIsDifferentFromAnOmittedAvatar() throws Exception {
    var request = mapper.readValue("{\"avatarUrl\":null}", UpdatePersonPreferencesRequest.class);
    assertTrue(request.avatarProvided());
    assertNull(request.avatarUrl());
    assertNull(request.language());
  }

  @Test
  void unsupportedLanguageIsRejectedAndExistingConstructorsDefaultToSystem() {
    assertThrows(
        com.fasterxml.jackson.core.JsonProcessingException.class,
        () -> mapper.readValue("{\"language\":\"ES\"}", UpdatePersonPreferencesRequest.class));
    assertEquals(
        PersonPreferences.Language.SYSTEM,
        new PersonPreferences(PersonPreferences.Theme.DARK, null).language());
  }
}
