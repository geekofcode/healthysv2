package org.novasos.healthysv2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.profilecontract.api.ContractAccess;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** No business data is accessible before the selected profile has signed its contract. */
@Configuration(proxyBeanMethods = false)
class ProfileContractGuard implements WebMvcConfigurer {
  private final CurrentUserContext users;
  private final ContractAccess contracts;

  ProfileContractGuard(CurrentUserContext users, ContractAccess contracts) {
    this.users = users;
    this.contracts = contracts;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new Guard(users, contracts)).addPathPatterns("/api/v1/**").order(-95);
  }

  static class Guard implements HandlerInterceptor {
    private final CurrentUserContext users;
    private final ContractAccess contracts;

    Guard(CurrentUserContext users, ContractAccess contracts) {
      this.users = users;
      this.contracts = contracts;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
      String path = request.getRequestURI().substring(request.getContextPath().length());
      if (request.getMethod().equals("OPTIONS") || bootstrap(path)) return true;
      var user = users.current();
      if (path.equals("/api/v1/admin/contract-templates")
          || path.startsWith("/api/v1/admin/contract-templates/")) {
        if (!user.has("PLATFORM_ADMIN")) throw new AccessDeniedException("PLATFORM_ADMIN_REQUIRED");
        return true;
      }
      if (user.profileId() == null || user.roles().isEmpty())
        throw new AccessDeniedException("PROFILE_SELECTION_REQUIRED");
      contracts.requireSigned(user.personId(), user.profileId());
      return true;
    }

    private static boolean bootstrap(String path) {
      return path.startsWith("/api/v1/public/")
          || path.equals("/api/v1/licensing/stripe/webhook")
          || path.equals("/api/v1/notifications/devices/revoke")
          || path.equals("/api/v1/persons/me")
          || path.equals("/api/v1/persons/me/profile")
          || path.equals("/api/v1/me/access-profiles")
          || path.equals("/api/v1/me/patient-profiles")
          || path.startsWith("/api/v1/me/profile-contracts/")
          || path.equals("/api/v1/me/data-deletion")
          || path.startsWith("/api/v1/me/data-deletion/")
          || path.equals("/api/v1/patients/me/provision")
          || path.equals("/api/v1/licensing/my-memberships")
          || path.matches(
              "/api/v1/licensing/organizations/[0-9a-fA-F-]{36}/team/[0-9a-fA-F-]{36}/accept");
    }
  }
}
