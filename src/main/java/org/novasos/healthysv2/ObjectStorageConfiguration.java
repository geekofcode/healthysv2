package org.novasos.healthysv2;

import java.net.URI;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObjectStorageConfiguration.StorageProperties.class)
public class ObjectStorageConfiguration {

    @Bean
    S3Client objectStorageClient(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                properties.accessKey(),
                                properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build())
                .build();
    }

    @Validated
    @ConfigurationProperties("healthys.storage")
    public record StorageProperties(
            @NotBlank String endpoint,
            @NotBlank String region,
            @NotBlank String bucket,
            @NotBlank String accessKey,
            @NotBlank String secretKey,
            boolean pathStyleAccess) {
    }
}
