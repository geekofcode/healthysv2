package org.novasos.healthysv2.document;
import org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties;import org.springframework.boot.health.contributor.*;import org.springframework.stereotype.Component;import software.amazon.awssdk.services.s3.S3Client;import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
@Component("objectStorage") class DocumentStorageHealthIndicator implements HealthIndicator{
 private final S3Client client;private final StorageProperties properties;DocumentStorageHealthIndicator(S3Client client,StorageProperties properties){this.client=client;this.properties=properties;}
 @Override public Health health(){try{client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());return Health.up().withDetail("bucket",properties.bucket()).build();}catch(RuntimeException exception){return Health.down().withDetail("bucket",properties.bucket()).withException(exception).build();}}
}
