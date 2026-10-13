package org.novasos.healthysv2.lifecycle;

import java.util.*;
import org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Removes the exact object, including retained versions and delete markers. */
@Component
class PrivateObjectPurger {
  private static final int MAX_PAGES = 100;
  private final S3Client storage;
  private final String bucket;

  PrivateObjectPurger(S3Client storage, StorageProperties properties) {
    this.storage = storage;
    this.bucket = properties.bucket();
  }

  void delete(String key) {
    var versioning =
        storage.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucket).build());
    if (versioning.status() == null) {
      storage.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
      return;
    }
    List<Version> exact = new ArrayList<>();
    String keyMarker = null, versionMarker = null;
    Set<String> cursors = new HashSet<>();
    boolean complete = false;
    for (int page = 0; page < MAX_PAGES; page++) {
      var result =
          storage.listObjectVersions(
              ListObjectVersionsRequest.builder()
                  .bucket(bucket)
                  .prefix(key)
                  .keyMarker(keyMarker)
                  .versionIdMarker(versionMarker)
                  .maxKeys(1000)
                  .build());
      for (var version : result.versions())
        if (key.equals(version.key())) exact.add(new Version(key, version.versionId()));
      for (var marker : result.deleteMarkers())
        if (key.equals(marker.key())) exact.add(new Version(key, marker.versionId()));
      if (!Boolean.TRUE.equals(result.isTruncated())) {
        complete = true;
        break;
      }
      keyMarker = result.nextKeyMarker();
      versionMarker = result.nextVersionIdMarker();
      if (keyMarker == null || !cursors.add(keyMarker + "\u0000" + versionMarker))
        throw new IllegalStateException("INVALID_OBJECT_VERSION_CURSOR");
    }
    if (!complete) throw new IllegalStateException("OBJECT_VERSION_BATCH_LIMIT");
    for (var version : exact) {
      if (version.id() == null) throw new IllegalStateException("OBJECT_VERSION_ID_REQUIRED");
      storage.deleteObject(
          DeleteObjectRequest.builder()
              .bucket(bucket)
              .key(version.key())
              .versionId(version.id())
              .build());
    }
    // Never issue an unversioned DELETE here: it would recreate a delete marker.
    var remaining =
        storage.listObjectVersions(
            ListObjectVersionsRequest.builder().bucket(bucket).prefix(key).maxKeys(1).build());
    if (remaining.versions().stream().anyMatch(v -> key.equals(v.key()))
        || remaining.deleteMarkers().stream().anyMatch(v -> key.equals(v.key())))
      throw new IllegalStateException("OBJECT_VERSION_RETRY_REQUIRED");
  }

  private record Version(String key, String id) {}
}
