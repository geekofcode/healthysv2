package org.novasos.healthysv2.care;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class AiAudioStorageTests {
  @Test
  void cleanupDeletesExpiredOrphanAudioButRetainsFreshUpload() {
    var client = mock(S3Client.class);
    var storage =
        new AiAudioStorage(
            client,
            new StorageProperties("http://minio", "us-east-1", "bucket", "key", "secret", true));
    when(client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(
            ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(
                    S3Object.builder()
                        .key("ai-temporary/old")
                        .lastModified(Instant.now().minus(Duration.ofHours(2)))
                        .build(),
                    S3Object.builder()
                        .key("ai-temporary/fresh")
                        .lastModified(Instant.now())
                        .build())
                .build());
    storage.sweep(Duration.ofHours(1));
    verify(client)
        .deleteObject(argThat((DeleteObjectRequest r) -> r.key().equals("ai-temporary/old")));
    verify(client, never())
        .deleteObject(argThat((DeleteObjectRequest r) -> r.key().equals("ai-temporary/fresh")));
    verify(client)
        .listObjectsV2(
            argThat(
                (ListObjectsV2Request r) ->
                    r.prefix().equals("ai-temporary/") && r.maxKeys() == 100));
  }
}
