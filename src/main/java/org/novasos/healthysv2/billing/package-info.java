@org.springframework.modulith.ApplicationModule(
        displayName = "Billing and payments",
        allowedDependencies = {"patient", "shared", "licensing::api", "care::api"})
package org.novasos.healthysv2.billing;
