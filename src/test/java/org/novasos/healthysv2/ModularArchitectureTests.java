package org.novasos.healthysv2;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularArchitectureTests {

    private final ApplicationModules modules =
            ApplicationModules.of(HealthysV2Application.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }
}
