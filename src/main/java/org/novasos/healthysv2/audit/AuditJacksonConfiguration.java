package org.novasos.healthysv2.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuditJacksonConfiguration {
    @Bean
    ObjectMapper auditObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
