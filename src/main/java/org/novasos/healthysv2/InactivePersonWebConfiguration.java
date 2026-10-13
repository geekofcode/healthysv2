package org.novasos.healthysv2;

import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** A local deletion tombstone revokes API usage even while an external IdP token survives. */
@Configuration(proxyBeanMethods = false)
class InactivePersonWebConfiguration implements WebMvcConfigurer {
  private final JdbcTemplate db;

  InactivePersonWebConfiguration(JdbcTemplate db) {
    this.db = db;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new Guard(db))
        .addPathPatterns("/api/v1/**")
        .excludePathPatterns(
            "/api/v1/me/data-deletion",
            "/api/v1/me/data-deletion/**",
            "/api/v1/public/**",
            "/api/v1/licensing/stripe/webhook",
            "/api/v1/notifications/devices/revoke")
        .order(-100);
  }

  static class Guard implements HandlerInterceptor {
    private final JdbcTemplate db;

    Guard(JdbcTemplate db) {
      this.db = db;
    }

    @Override
    public boolean preHandle(
        jakarta.servlet.http.HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response,
        Object handler) {
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof JwtAuthenticationToken jwt) {
        UUID subject;
        try {
          subject = UUID.fromString(jwt.getToken().getSubject());
        } catch (IllegalArgumentException e) {
          throw new AccessDeniedException("INVALID_SUBJECT");
        }
        String status =
            db.query(
                "select status from identity.person where keycloak_user_id=?",
                rs -> rs.next() ? rs.getString(1) : null,
                subject);
        if ("INACTIVE".equals(status)) throw new AccessDeniedException("ACCOUNT_DATA_DELETED");
      }
      return true;
    }
  }
}
