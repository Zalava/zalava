package org.zalava.managed.application.port.out;

import java.util.List;
import org.zalava.managed.application.ManagedServiceRecord;

/** Narrow engine-neutral lifecycle boundary; Docker and Podman adapters are later work. */
public interface OciServiceEngine {
  Observation inspect(String serviceId);

  void create(ManagedServiceRecord record);

  void start(String serviceId);

  void stop(String serviceId);

  /** Removes the owned, existing service resource so a later create can re-use the name. */
  void remove(String serviceId);

  /** Bounded recent engine log lines; diagnostic, best-effort, engine-dependent retention. */
  List<String> recentLogs(String serviceId, int maxLines);

  record Observation(
      boolean exists, boolean running, boolean ready, String ownerModuleId, String dataIdentity) {}
}
