package org.novasos.healthysv2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Commercial checks supplement, never replace, clinical resource authorization. */
@Configuration(proxyBeanMethods=false)
class SubscriptionWebConfiguration implements WebMvcConfigurer {
    private final LicensingAccess licensing;
    private final CurrentUserContext users;
    SubscriptionWebConfiguration(LicensingAccess licensing, CurrentUserContext users) { this.licensing=licensing; this.users=users; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new WriteGuard(licensing, users)).addPathPatterns("/api/v1/persons", "/api/v1/patients/**", "/api/v1/consultations/**", "/api/v1/appointments/**",
                "/api/v1/care/**", "/api/v1/episodes/**", "/api/v1/visits/**", "/api/v1/invoices/**",
                "/api/v1/billing/**", "/api/v1/prescriptions/**", "/api/v1/documents/**")
                .excludePathPatterns("/api/v1/patients/me/**", "/api/v1/patients/me");
    }
    static class WriteGuard implements HandlerInterceptor {
        private final LicensingAccess licensing;
        private final CurrentUserContext users;
        WriteGuard(LicensingAccess licensing, CurrentUserContext users) { this.licensing=licensing; this.users=users; }
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) return true;
                var user=users.current();
                String uri = request.getRequestURI();
                boolean patientDecision = uri.matches("/api/v1/patients/[0-9a-fA-F-]{36}/access-revocations")
                        || uri.matches("/api/v1/patients/[0-9a-fA-F-]{36}/consents(/[0-9a-fA-F-]{36}/revoke)?")
                        || uri.matches("/api/v1/patients/[0-9a-fA-F-]{36}/access-requests/[0-9a-fA-F-]{36}/(accept|revoke)")
                        || ("DELETE".equals(request.getMethod()) && uri.matches("/api/v1/patients/[0-9a-fA-F-]{36}/access-grants/[0-9a-fA-F-]{36}"));
                if (patientDecision && user.has("PATIENT")) return true;
                if (user.organizationId()==null) throw new AccessDeniedException("ORGANIZATION_CONTEXT_REQUIRED");
                licensing.requireMember(user.organizationId());
                licensing.requireActive(user.organizationId());
                if (uri.contains("/ai-jobs") || uri.matches("/api/v1/care/visits/[0-9a-fA-F-]{36}/ai/.*")) licensing.requireAi(user.organizationId());
                return true;
            }
    }
}
