package org.zalava.assistant.models.configuration.application.port.in;

/** One client/options generation for the complete operation, including streamed tool rounds. */
public interface ModelGenerationSource<T> {
  Session<T> acquire();

  interface Session<T> extends AutoCloseable {
    T client();

    boolean toolCallingEnabled();

    @Override
    void close();
  }

  static <T> Session<T> fixed(T client, boolean tools) {
    return new Session<T>() {
      public T client() {
        return client;
      }

      public boolean toolCallingEnabled() {
        return tools;
      }

      public void close() {}
    };
  }
}
