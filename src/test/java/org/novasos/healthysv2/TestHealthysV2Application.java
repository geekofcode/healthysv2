package org.novasos.healthysv2;

import org.springframework.boot.SpringApplication;

public class TestHealthysV2Application {

    public static void main(String[] args) {
        SpringApplication.from(HealthysV2Application::main).with(TestcontainersConfiguration.class).run(args);
    }

}
