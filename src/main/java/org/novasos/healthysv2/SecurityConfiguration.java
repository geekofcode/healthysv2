package org.novasos.healthysv2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityConfiguration.SecurityProperties.class)
@ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
class SecurityConfiguration {

    static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/ws",
            "/ws/**",
            "/api/v1/public/**"
    };

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS)
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole(HealthysRole.PLATFORM_ADMIN.name())
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            SecurityProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                keycloakAuthorities(properties.apiClientId()));
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                HttpHeaders.ACCEPT_LANGUAGE,
                "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of(
                HttpHeaders.LOCATION,
                "X-Correlation-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(properties.cors().maxAgeSeconds());

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private Converter<Jwt, Collection<GrantedAuthority>> keycloakAuthorities(
            String apiClientId) {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

        return jwt -> {
            Collection<GrantedAuthority> authorities =
                    new ArrayList<>(scopes.convert(jwt));
            addRoles(authorities, realmRoles(jwt));
            addRoles(authorities, clientRoles(jwt, apiClientId));
            return authorities;
        };
    }

    private Collection<String> realmRoles(Jwt jwt) {
        return rolesFrom(jwt.getClaimAsMap("realm_access"));
    }

    private Collection<String> clientRoles(Jwt jwt, String apiClientId) {
        Map<String, Object> resourceAccess =
                jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }

        Object clientAccess = resourceAccess.get(apiClientId);
        if (clientAccess instanceof Map<?, ?> access) {
            return rolesFrom(access);
        }
        return List.of();
    }

    private Collection<String> rolesFrom(Map<?, ?> access) {
        if (access == null) {
            return List.of();
        }

        Object roles = access.get("roles");
        if (roles instanceof Collection<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private void addRoles(
            Collection<GrantedAuthority> authorities,
            Collection<String> roles) {
        roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);
    }

    @Validated
    @ConfigurationProperties("healthys.security")
    record SecurityProperties(
            @NotBlank String apiClientId,
            @Valid CorsProperties cors) {

        record CorsProperties(
                @NotEmpty List<@NotBlank String> allowedOrigins,
                long maxAgeSeconds) {
        }
    }
}
