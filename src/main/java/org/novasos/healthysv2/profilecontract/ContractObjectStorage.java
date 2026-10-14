package org.novasos.healthysv2.profilecontract;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
class ContractObjectStorage {
  private final S3Client client;
  private final String bucket;

  ContractObjectStorage(S3Client client, @Value("${healthys.storage.bucket}") String bucket) {
    this.client = client;
    this.bucket = bucket;
  }

  void put(String key, byte[] bytes, String type) {
    client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).build(),
        RequestBody.fromBytes(bytes));
  }

  byte[] get(String key) {
    return client
        .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
        .asByteArray();
  }
}
