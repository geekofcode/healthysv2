@org.springframework.modulith.ApplicationModule(
    displayName = "Prescription management",
    allowedDependencies = {
      "patient",
      "shared",
      "audit",
      "care::api",
      "licensing::api",
      "organizationaccess::api"
    })
package org.novasos.healthysv2.prescription;
