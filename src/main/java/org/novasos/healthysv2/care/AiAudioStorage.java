package org.novasos.healthysv2.care;

import org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
class AiAudioStorage {
  private final S3Client client;
  private final String bucket;
  private String continuation;

  AiAudioStorage(S3Client client, StorageProperties props) {
    this.client = client;
    this.bucket = props.bucket();
  }

  void put(String key, byte[] bytes, String mime) {
    client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(mime).build(),
        RequestBody.fromBytes(bytes));
  }

  byte[] get(String key) {
    return client
        .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
        .asByteArray();
  }

  synchronized void sweep(java.time.Duration ttl) {
    java.time.Instant cutoff = java.time.Instant.now().minus(ttl);
    // Bounded pages per pass, continuation across passes; the namespace is dedicated to disposable
    // AI audio.
    for (int page = 0; page < 10; page++) {
      var response =
          client.listObjectsV2(
              ListObjectsV2Request.builder()
                  .bucket(bucket)
                  .prefix("ai-temporary/")
                  .continuationToken(continuation)
                  .maxKeys(100)
                  .build());
      for (var object : response.contents())
        if (object.lastModified() != null && object.lastModified().isBefore(cutoff))
          delete(object.key());
      continuation = response.nextContinuationToken();
      if (!Boolean.TRUE.equals(response.isTruncated())) {
        continuation = null;
        break;
      }
    }
  }

  void delete(String key) {
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }
}
