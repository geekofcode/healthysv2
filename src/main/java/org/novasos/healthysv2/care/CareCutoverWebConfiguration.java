package org.novasos.healthysv2.care;

import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Historical reads survive; retired business commands are blocked at the server boundary. */
@Configuration(proxyBeanMethods = false)
class CareCutoverWebConfiguration implements WebMvcConfigurer {
  private final CutoverService cutover;

  CareCutoverWebConfiguration(CutoverService cutover) {
    this.cutover = cutover;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            (HandlerInterceptor)
                new HandlerInterceptor() {
                  @Override
                  public boolean preHandle(
                      jakarta.servlet.http.HttpServletRequest request,
                      jakarta.servlet.http.HttpServletResponse response,
                      Object handler) {
                    if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()))
                      cutover.requireLegacyWrite();
                    return true;
                  }
                })
        .addPathPatterns(
            "/api/v1/appointments",
            "/api/v1/appointments/**",
            "/api/v1/consultations",
            "/api/v1/consultations/**",
            "/api/v1/prescriptions",
            "/api/v1/prescriptions/**",
            "/api/v1/invoices",
            "/api/v1/invoices/**")
        .order(-50);
  }
}
