package org.novasos.healthysv2.teleconsultation;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LiveKitPropertiesTests {
    @Test void requiresSecureProviderOutsideLocalhostAndBoundsCredentialLifetime(){
        var properties=new LiveKitProperties();properties.setApiKey("key");properties.setApiSecret("server-only-secret");
        properties.setUrl("ws://localhost:7880");assertThatCode(properties::validate).doesNotThrowAnyException();
        properties.setUrl("ws://livekit.example.org");assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setUrl("wss://livekit.example.org");assertThatCode(properties::validate).doesNotThrowAnyException();
        properties.setTokenTtlMinutes(16);assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setTokenTtlMinutes(0);assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
