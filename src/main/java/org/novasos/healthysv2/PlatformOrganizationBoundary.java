package org.novasos.healthysv2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** A platform administrator never inherits hospital operation access through another role. */
@Configuration(proxyBeanMethods = false)
class PlatformOrganizationBoundary implements WebMvcConfigurer {
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new Guard()).addPathPatterns("/api/v1/**").order(-90);
  }

  static class Guard implements HandlerInterceptor {
    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null
          || auth.getAuthorities().stream()
              .noneMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"))) return true;
      if (request.getMethod().equals("OPTIONS")) return true;
      String path = request.getRequestURI().substring(request.getContextPath().length());
      if (allowed(path, request.getMethod())) return true;
      throw new AccessDeniedException("PLATFORM_OPERATION_DENIED");
    }

    private boolean allowed(String path, String method) {
      if (path.startsWith("/api/v1/public/")
          || path.equals("/api/v1/persons/me")
          || path.startsWith("/api/v1/persons/me/")
          || path.equals("/api/v1/platform/accounts")
          || path.startsWith("/api/v1/platform/accounts/")
          || path.startsWith("/api/v1/me/")
          || path.equals("/api/v1/me")) return true;
      if (path.equals("/api/v1/organizations")
          || path.equals("/api/v1/organizations/types")
          || path.matches("/api/v1/organizations/[0-9a-fA-F-]{36}(/(activate|deactivate|status))?"))
        return true;
      if (path.equals("/api/v1/admin/contract-templates")
          || path.startsWith("/api/v1/admin/contract-templates/")
          || path.equals("/api/v1/admin/overview")
          || path.equals("/api/v1/admin/statistics")
          || path.equals("/api/v1/admin/platform-audit")
          || path.equals("/api/v1/admin/account-authentication")
          || path.equals("/api/v1/admin/subscriptions")
          || path.matches("/api/v1/admin/organizations/[0-9a-fA-F-]{36}/statistics")
          || path.startsWith("/api/v1/admin/accounts")) return true;
      if (path.equals("/api/v1/care/cutover") || path.equals("/api/v1/care/cutover/activate"))
        return true;
      if (path.equals("/api/v1/licensing/plans") || path.startsWith("/api/v1/licensing/plans/"))
        return true;
      return path.matches(
              "/api/v1/licensing/organizations/[0-9a-fA-F-]{36}/(entitlements|invoices|checkout|subscription|cancel|portal)")
          || path.equals("/api/v1/licensing/my-memberships");
    }
  }
}
