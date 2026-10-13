package org.novasos.healthysv2.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class PrivateObjectPurgerTests {
  private final S3Client s3 = mock(S3Client.class);
  private final PrivateObjectPurger purger =
      new PrivateObjectPurger(
          s3, new StorageProperties("http://localhost", "test", "bucket", "test", "test", true));

  @Test
  void deletesAllExactVersionsAndMarkersWithoutTouchingPrefixNeighbors() {
    when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class)))
        .thenReturn(
            GetBucketVersioningResponse.builder().status(BucketVersioningStatus.ENABLED).build());
    when(s3.listObjectVersions(any(ListObjectVersionsRequest.class)))
        .thenReturn(
            ListObjectVersionsResponse.builder()
                .isTruncated(true)
                .nextKeyMarker("private/key")
                .nextVersionIdMarker("v1")
                .versions(
                    ObjectVersion.builder().key("private/key").versionId("v1").build(),
                    ObjectVersion.builder().key("private/key-neighbor").versionId("other").build())
                .build(),
            ListObjectVersionsResponse.builder()
                .isTruncated(false)
                .versions(ObjectVersion.builder().key("private/key").versionId("v0").build())
                .deleteMarkers(
                    DeleteMarkerEntry.builder().key("private/key").versionId("marker").build())
                .build(),
            ListObjectVersionsResponse.builder()
                .isTruncated(false)
                .versions(
                    ObjectVersion.builder().key("private/key-neighbor").versionId("other").build())
                .build());
    purger.delete("private/key");
    var deletes = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3, times(3)).deleteObject(deletes.capture());
    assertThat(deletes.getAllValues())
        .allSatisfy(r -> assertThat(r.key()).isEqualTo("private/key"));
    assertThat(deletes.getAllValues())
        .extracting(DeleteObjectRequest::versionId)
        .containsExactly("v1", "v0", "marker");
    var lists = ArgumentCaptor.forClass(ListObjectVersionsRequest.class);
    verify(s3, times(3)).listObjectVersions(lists.capture());
    assertThat(lists.getAllValues().get(1).keyMarker()).isEqualTo("private/key");
    assertThat(lists.getAllValues().get(1).versionIdMarker()).isEqualTo("v1");
  }

  @Test
  void unversionedBucketUsesIdempotentPlainDelete() {
    when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class)))
        .thenReturn(GetBucketVersioningResponse.builder().build());
    purger.delete("private/key");
    verify(s3)
        .deleteObject(
            argThat(
                (DeleteObjectRequest r) -> r.key().equals("private/key") && r.versionId() == null));
    verify(s3, never()).listObjectVersions(any(ListObjectVersionsRequest.class));
  }

  @Test
  void refusesStuckPaginationBeforeDeletingAnything() {
    when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class)))
        .thenReturn(
            GetBucketVersioningResponse.builder().status(BucketVersioningStatus.SUSPENDED).build());
    when(s3.listObjectVersions(any(ListObjectVersionsRequest.class)))
        .thenReturn(
            ListObjectVersionsResponse.builder()
                .isTruncated(true)
                .nextKeyMarker("key")
                .nextVersionIdMarker("v1")
                .build());
    assertThatThrownBy(() -> purger.delete("key")).hasMessage("INVALID_OBJECT_VERSION_CURSOR");
    verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void concurrentNewVersionRequiresRetry() {
    when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class)))
        .thenReturn(
            GetBucketVersioningResponse.builder().status(BucketVersioningStatus.ENABLED).build());
    when(s3.listObjectVersions(any(ListObjectVersionsRequest.class)))
        .thenReturn(
            ListObjectVersionsResponse.builder().isTruncated(false).build(),
            ListObjectVersionsResponse.builder()
                .isTruncated(false)
                .versions(ObjectVersion.builder().key("key").versionId("new").build())
                .build());
    assertThatThrownBy(() -> purger.delete("key")).hasMessage("OBJECT_VERSION_RETRY_REQUIRED");
  }
}
