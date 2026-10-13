package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularArchitectureTests {

    private final ApplicationModules modules =
            ApplicationModules.of(HealthysV2Application.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void keepsPrescriptionPersistencePrivate() throws ClassNotFoundException {
        Class<?> prescription = Class.forName("org.novasos.healthysv2.prescription.Prescription");
        assertThat(Modifier.isPublic(prescription.getModifiers())).isFalse();
    }
}
