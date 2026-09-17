package org.novasos.healthysv2;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI healthysOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Healthys API")
                        .description("API de la plateforme de santé Healthys")
                        .version("v1"));
    }
}
