package org.novasos.healthysv2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Establishment CRUD permissions supplement clinical consent and resource ownership. */
@Configuration(proxyBeanMethods = false)
class HospitalOperationPermissions implements WebMvcConfigurer {
  private final OrganizationPermissionAccess permissions;
  private final CurrentUserContext users;

  HospitalOperationPermissions(OrganizationPermissionAccess permissions, CurrentUserContext users) {
    this.permissions = permissions;
    this.users = users;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new Guard(permissions, users)).addPathPatterns("/api/v1/**").order(-80);
  }

  static class Guard implements HandlerInterceptor {
    private final OrganizationPermissionAccess permissions;
    private final CurrentUserContext users;

    Guard(OrganizationPermissionAccess permissions, CurrentUserContext users) {
      this.permissions = permissions;
      this.users = users;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
      String path = request.getRequestURI().substring(request.getContextPath().length());
      String resource = resource(path);
      if (resource == null && path.equals("/api/v1/persons") && request.getMethod().equals("POST"))
        resource = "patients";
      if (resource == null || request.getMethod().equals("OPTIONS")) return true;
      var user = users.current();
      if (user.has("PLATFORM_ADMIN")) throw new AccessDeniedException("PLATFORM_OPERATION_DENIED");
      // Patient self routes continue to use their stricter ownership/consent services.
      if (path.equals("/api/v1/patients/me")
          || path.startsWith("/api/v1/patients/me/")
          || path.equals("/api/v1/care/visits/me")) return true;
      if (user.has("PATIENT")
          && user.organizationId() == null
          && patientSelfOperation(path, request.getMethod())) return true;
      if (user.organizationId() == null)
        throw new AccessDeniedException("ORGANIZATION_CONTEXT_REQUIRED");
      permissions.require(user.organizationId(), resource, action(request.getMethod(), path));
      return true;
    }

    private static boolean patientSelfOperation(String path, String method) {
      // Downstream ownership checks are mandatory for these deliberately enumerated self actions.
      if (path.matches(
          "/api/v1/patients/[0-9a-fA-F-]{36}/(access-requests|access-grants|access-revocations|consents)(/.*)?"))
        return true;
      return (method.equals("GET") || method.equals("HEAD"))
          && (path.equals("/api/v1/invoices")
              || path.equals("/api/v1/prescriptions")
              || path.equals("/api/v1/documents")
              || path.startsWith("/api/v1/documents/")
              || path.startsWith("/api/v1/prescriptions/")
              || path.startsWith("/api/v1/invoices/")
              || path.matches(
                  "/api/v1/patients/[0-9a-fA-F-]{36}/(care-history|medical-record)(/.*)?")
              || path.matches("/api/v1/care/visits/[0-9a-fA-F-]{36}/(report|documents)(/.*)?"));
    }

    static String action(String method, String path) {
      if (method.equals("POST")
          && (path.matches(
                  ".*/(start|complete|finalize|corrections|reschedule|schedule|presence|next|cancel|accept|revoke|activate|deactivate|resolve)$")
              || path.endsWith("/report/corrections"))) return "UPDATE";
      return action(method);
    }

    static String action(String method) {
      return switch (method) {
        case "GET", "HEAD" -> "READ";
        case "DELETE" -> "DELETE";
        case "PUT", "PATCH" -> "UPDATE";
        default -> "CREATE";
      };
    }

    static String resource(String p) {
      if (p.matches("/api/v1/organizations/[0-9a-fA-F-]{36}/audit(/.*)?")) return "audit";
      if (p.startsWith("/api/v1/patients")) {
        if (p.contains("/access-") || p.contains("/consents") || p.contains("/care-relationships"))
          return "patientaccess";
        if (p.contains("/medical-record")
            || p.contains("/care-history")
            || p.matches("/api/v1/patients/[0-9a-fA-F-]{36}")
            || p.matches(
                "/api/v1/patients/[0-9a-fA-F-]{36}/(allergies|chronic-diseases|medical-histories|surgical-histories|family-histories|disabilities|notes|flags|emergency-profile)(/.*)?"))
          return "clinicalrecords";
        return "patients";
      }
      if (p.startsWith("/api/v1/documents")) return "documents";
      if (p.startsWith("/api/v1/prescriptions")) return "prescriptions";
      if (p.startsWith("/api/v1/invoices")) return "invoices";
      if (p.startsWith("/api/v1/appointments")) return "scheduling";
      if (p.startsWith("/api/v1/consultations")) return "reports";
      if (p.startsWith("/api/v1/billing/care")) {
        if (p.contains("cash-report")) return "cashreports";
        if (p.contains("cash-closures")) return "cashclosures";
        if (p.contains("tariff")) return "tariffs";
        if (p.contains("refund")) return "refunds";
        if (p.contains("payment") || p.contains("receipt") || p.endsWith("/cash"))
          return "payments";
        return "invoices";
      }
      if (p.startsWith("/api/v1/care/episodes")) {
        if (p.contains("/intake")) return "intakes";
        if (p.contains("/follow-up")) return "visits";
        return "episodes";
      }
      if (p.startsWith("/api/v1/care/")) {
        if (p.contains("/cutover")) return null;
        if (p.contains("/ai/")) return "ai";
        if (p.contains("/documents")) return "documents";
        if (p.contains("/prescription")) return "prescriptions";
        if (p.contains("/queue")) return "queue";
        if (p.contains("/calendar")
            || p.contains("/schedule")
            || p.contains("/reschedule")
            || p.contains("/assign")) return "scheduling";
        if (p.contains("/intake")) return "intakes";
        if (p.contains("/report")) return "reports";
        return "visits";
      }
      return null;
    }
  }
}
