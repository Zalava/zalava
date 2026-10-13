package org.zalava.assistant.models.configuration.adapter.out.spring;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.ai.chat.client.ChatClient;
import org.zalava.assistant.models.configuration.application.port.in.ModelGenerationSource;
import org.zalava.assistant.models.configuration.application.port.in.ModelProviderState;
import org.zalava.assistant.models.configuration.application.port.out.ModelProviderActivation;

/**
 * Serializes activation/shutdown; retired clients remain alive until their final operation ends.
 */
public final class LiveModelProviderRuntime
    implements ModelProviderActivation,
        ModelProviderState,
        ModelGenerationSource<ChatClient>,
        AutoCloseable {
  public record Client(ChatClient chatClient, Runnable close) {}

  private static final Logger LOG = System.getLogger(LiveModelProviderRuntime.class.getName());
  private final BiFunction<String, String, String> initialProperties;
  private final Function<Map<String, Object>, Client> factory;
  private final ReentrantLock transition = new ReentrantLock();
  private Generation current;
  private boolean closed;

  public LiveModelProviderRuntime(
      ChatClient initial,
      BiFunction<String, String, String> properties,
      Function<Map<String, Object>, Client> factory) {
    this.initialProperties = properties;
    this.factory = factory;
    current =
        new Generation(
            new Client(initial, () -> {}),
            Map.of(),
            Boolean.parseBoolean(properties.apply("zalava.model.tool-calling-enabled", "true")));
  }

  @Override
  public synchronized String property(String name, String fallback) {
    Object value = current.properties.get(name);
    return value == null ? initialProperties.apply(name, fallback) : String.valueOf(value);
  }

  @Override
  public synchronized Session<ChatClient> acquire() {
    if (closed) throw new IllegalStateException("Provider runtime is shutting down");
    Generation selected = current;
    selected.users++;
    return new Session<ChatClient>() {
      private final AtomicBoolean released = new AtomicBoolean();

      public ChatClient client() {
        return selected.client.chatClient();
      }

      public boolean toolCallingEnabled() {
        return selected.tools;
      }

      public void close() {
        if (released.compareAndSet(false, true)) release(selected);
      }
    };
  }

  @Override
  public Prepared prepare(Map<String, Object> properties) {
    transition.lock();
    try {
      synchronized (this) {
        if (closed) throw new IllegalStateException("Provider runtime is shutting down");
      }
      Map<String, Object> candidate = Map.copyOf(properties);
      Generation prepared =
          new Generation(
              factory.apply(candidate),
              candidate,
              Boolean.parseBoolean(
                  String.valueOf(
                      candidate.getOrDefault("zalava.model.tool-calling-enabled", true))));
      return new Prepared() {
        private boolean committed;
        private boolean finished;

        public void commit() {
          if (finished) throw new IllegalStateException("Provider preparation is closed");
          if (committed) return;
          synchronized (LiveModelProviderRuntime.this) {
            Generation old = current;
            current = prepared;
            committed = true;
            retire(old);
          }
        }

        public void close() {
          if (finished) return;
          finished = true;
          try {
            if (!committed)
              synchronized (LiveModelProviderRuntime.this) {
                retire(prepared);
              }
          } finally {
            transition.unlock();
          }
        }
      };
    } catch (RuntimeException failure) {
      transition.unlock();
      throw failure;
    }
  }

  private synchronized void release(Generation generation) {
    generation.users--;
    disposeIfUnused(generation);
  }

  private void retire(Generation generation) {
    generation.retired = true;
    disposeIfUnused(generation);
  }

  private void disposeIfUnused(Generation generation) {
    if (!generation.retired || generation.users != 0 || generation.disposed) return;
    generation.disposed = true;
    try {
      generation.client.close().run();
    } catch (RuntimeException failure) {
      LOG.log(
          Level.WARNING,
          "Retired provider resource cleanup failed ({0})",
          failure.getClass().getSimpleName());
    }
  }

  @Override
  public void close() {
    transition.lock();
    try {
      synchronized (this) {
        if (!closed) {
          closed = true;
          retire(current);
        }
      }
    } finally {
      transition.unlock();
    }
  }

  private static final class Generation {
    final Client client;
    final Map<String, Object> properties;
    final boolean tools;
    int users;
    boolean retired;
    boolean disposed;

    Generation(Client client, Map<String, Object> properties, boolean tools) {
      this.client = client;
      this.properties = properties;
      this.tools = tools;
    }
  }
}
