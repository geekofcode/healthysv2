package org.novasos.healthysv2.lifecycle;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class DeletionWorkerTests {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PrivateObjectPurger purger = mock(PrivateObjectPurger.class);
  private final DeletionWorker worker =
      spy(
          new DeletionWorker(
              jdbc,
              mock(TransactionTemplate.class),
              purger,
              new StorageProperties("http://localhost", "test", "bucket", "test", "test", true),
              true,
              false));

  @Test
  void normalLargeBatchProgressRequeuesWithoutCountingFailure() {
    var lease = new DeletionWorker.Lease(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    doReturn(true).when(worker).leaseValid(lease);
    doThrow(new DeletionWorker.PartialProgress()).when(worker).drainOutbox(lease.id(), lease);
    worker.process(lease);
    verify(jdbc)
        .update(contains("attempts=greatest(attempts-1,0)"), eq(lease.id()), eq(lease.token()));
    verify(jdbc, never())
        .update(contains("PURGE_RETRY_REQUIRED"), eq(lease.id()), eq(lease.token()));
  }

  @Test
  void expiredLeaseCannotDrainOrFinalize() {
    var lease = new DeletionWorker.Lease(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    doReturn(false).when(worker).leaseValid(lease);
    worker.process(lease);
    verify(worker, never()).drainOutbox(any(), any());
    verify(worker, never()).processLocked(any());
    verifyNoInteractions(purger);
  }

  @Test
  void deletingAClinicianDoesNotEraseAnUnlinkedUploadFromAnotherPatientsRecord() {
    UUID document = UUID.randomUUID(), request = UUID.randomUUID();
    when(jdbc.queryForList(contains("select storage_key"), eq(String.class), eq(document)))
        .thenReturn(List.of("private/key"));
    when(jdbc.queryForObject(
            contains("p.person_id<>r.person_id"), eq(Integer.class), eq(request), eq(document)))
        .thenReturn(1);
    org.junit.jupiter.api.Assertions.assertFalse(worker.stageDocument(document, request));
    verify(jdbc, never()).update(contains("status='ARCHIVED'"), any(Object[].class));
    verifyNoInteractions(purger);
  }
}
