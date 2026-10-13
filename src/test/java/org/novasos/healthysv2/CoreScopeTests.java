package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Guards against accidentally restoring retired HTTP APIs, jobs, or WebSocket configuration. */
class CoreScopeTests {
  private static final String BASE = "org.novasos.healthysv2";
  private static final List<String> RETIRED =
      List.of("teleconsultation", "maternalandchildhealth", "laboratory", "pharmacy", "messaging");

  @Test
  void noRetiredRuntimeComponentIsPackaged() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
    for (String module : RETIRED) {
      assertThat(scanner.findCandidateComponents(BASE + "." + module))
          .as("Retired module %s cannot register APIs or background jobs", module)
          .isEmpty();
    }
  }

  @Test
  void noRetiredRouteIsAdvertised() throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    for (var candidate : scanner.findCandidateComponents(BASE)) {
      var controller = Class.forName(candidate.getBeanClassName());
      // Test fixtures are on the test classpath but are not shipped in the application.
      if (!controller
          .getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .equals(HealthysV2Application.class.getProtectionDomain().getCodeSource().getLocation()))
        continue;
      var classMapping =
          org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
              controller, RequestMapping.class);
      if (classMapping != null) assertNoRetiredRoute(new RequestMapping[] {classMapping});
      for (var method : controller.getDeclaredMethods()) {
        var mapping =
            org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                method, RequestMapping.class);
        if (mapping != null) assertNoRetiredRoute(new RequestMapping[] {mapping});
      }
    }
  }

  private void assertNoRetiredRoute(RequestMapping[] mappings) {
    for (var mapping : mappings) {
      for (String path : mapping.path()) {
        assertThat(path)
            .doesNotContain(
                "video-sessions",
                "teleconsult",
                "maternal",
                "pregnanc",
                "child-health",
                "lab-orders",
                "lab-results",
                "laborator",
                "pharmacy",
                "conversations",
                "/ws");
      }
    }
  }

  @Test
  void cannotCreateRetiredProfessionalTypesThroughCoreAdministration() {
    try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (String type : List.of("LAB_TECHNICIAN", "PHARMACIST")) {
        assertThat(
                validator.validate(
                    new org.novasos.healthysv2.professional.api.ProfessionalDtos
                        .ProfessionalRequest(java.util.UUID.randomUUID(), "P-1", type, "ACTIVE")))
            .isNotEmpty();
      }
      assertThat(
              validator.validate(
                  new org.novasos.healthysv2.professional.api.ProfessionalDtos.ProfessionalRequest(
                      java.util.UUID.randomUUID(), "P-1", "DOCTOR", "ACTIVE")))
          .isEmpty();
    }
  }
}
