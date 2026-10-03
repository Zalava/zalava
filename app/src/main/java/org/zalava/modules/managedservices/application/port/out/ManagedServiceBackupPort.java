package org.zalava.modules.managedservices.application.port.out;

import java.util.Objects;
import org.zalava.modules.managedservices.application.ManagedServiceRecord;

/**
 * Application-owned backup boundary executed by Zalava itself, never by a module. A backup covers
 * exactly the data paths the service record declares; refusing silent no-ops keeps "backed up"
 * meaningful for rollback decisions.
 */
public interface ManagedServiceBackupPort {

  /**
   * Archives the declared data paths of a stopped service; throws when there is nothing to back up.
   */
  BackupArtifact execute(ManagedServiceRecord record);

  /** Restores a previously taken backup onto the service's declared data paths. */
  void restore(ManagedServiceRecord record, String backupLocation);

  /** Immutable handle to one stored backup with its captured creation time. */
  record BackupArtifact(String serviceId, java.time.Instant createdAt, String location) {

    public BackupArtifact {
      if (serviceId == null || serviceId.isBlank()) {
        throw new IllegalArgumentException("serviceId must not be blank");
      }
      Objects.requireNonNull(createdAt, "createdAt");
      if (location == null || location.isBlank()) {
        throw new IllegalArgumentException("location must not be blank");
      }
    }
  }
}
