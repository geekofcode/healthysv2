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
    void exposesPrescriptionContractWithoutSharingJpaEntities() throws ClassNotFoundException {
        Class<?> contract = Class.forName("org.novasos.healthysv2.prescription.PrescriptionDispensing");
        Class<?> prescription = Class.forName("org.novasos.healthysv2.prescription.Prescription");
        Class<?> dispense = Class.forName("org.novasos.healthysv2.pharmacy.Dispense");

        assertThat(Modifier.isPublic(contract.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(prescription.getModifiers())).isFalse();
        assertThat(dispense.getDeclaredFields())
                .noneMatch(field -> field.getType().getPackageName().equals("org.novasos.healthysv2.prescription"));
    }
}
