package org.zalava.assistant.models.configuration.application.port.out;

import java.util.Map;

/** Prepare before persistence; commit is the final in-memory activation, with no external I/O. */
public interface ModelProviderActivation {
  Prepared prepare(Map<String, Object> properties);

  interface Prepared extends AutoCloseable {
    void commit();

    @Override
    void close();
  }

  static ModelProviderActivation onRestart() {
    return properties ->
        new Prepared() {
          public void commit() {}

          public void close() {}
        };
  }
}
