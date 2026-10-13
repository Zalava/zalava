package org.zalava.assistant.models.configuration.adapter.out.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration;
import org.zalava.platform.configuration.application.port.out.ConfigurationStore;

class LiveModelProviderRuntimeTest {
  private static ChatClient client() {
    ChatModel model = prompt -> null;
    return ChatClient.builder(model).build();
  }

  private static Map<String, Object> properties(String model, boolean tools) {
    return Map.of("spring.ai.model.chat", model, "zalava.model.tool-calling-enabled", tools);
  }

  @Test
  void retiredGenerationClosesOnlyAfterItsLastOperationAndOptionsStayPaired() {
    AtomicInteger closes = new AtomicInteger();
    ChatClient initial = client();
    try (var runtime =
        new LiveModelProviderRuntime(
            initial,
            (key, fallback) -> fallback,
            props -> new LiveModelProviderRuntime.Client(client(), closes::incrementAndGet))) {
      try (var prepared = runtime.prepare(properties("first", true))) {
        prepared.commit();
      }
      var first = runtime.acquire();
      var alsoFirst = runtime.acquire();
      try (var prepared = runtime.prepare(properties("second", false))) {
        prepared.commit();
        prepared.commit();
      }
      assertThat(closes.get()).isZero();
      assertThat(first.toolCallingEnabled()).isTrue();
      try (var second = runtime.acquire()) {
        assertThat(second.toolCallingEnabled()).isFalse();
        assertThat(second.client()).isNotSameAs(first.client());
      }
      first.close();
      first.close();
      assertThat(closes.get()).isZero();
      alsoFirst.close();
      assertThat(closes.get()).isEqualTo(1);
      assertThat(runtime.property("spring.ai.model.chat", "unknown")).isEqualTo("second");
    }
    assertThat(closes.get()).isEqualTo(2);
  }

  @Test
  void abandonedPreparationAndFailedPersistenceLeaveTheOriginalClientActive() throws Exception {
    AtomicInteger closes = new AtomicInteger();
    ChatClient initial = client();
    try (var runtime =
        new LiveModelProviderRuntime(
            initial,
            (key, fallback) -> fallback,
            props -> new LiveModelProviderRuntime.Client(client(), closes::incrementAndGet))) {
      var abandoned = runtime.prepare(properties("candidate", true));
      abandoned.close();
      abandoned.close();
      assertThatThrownBy(abandoned::commit).isInstanceOf(IllegalStateException.class);
      ConfigurationStore failing =
          new ConfigurationStore() {
            public Map<String, Object> read() {
              return Map.of();
            }

            public void write(Map<String, Object> values) throws IOException {
              throw new IOException("Disk full");
            }
          };
      var settings = new ModelProviderConfiguration(failing, runtime::property, runtime);
      assertThatThrownBy(
              () ->
                  settings.save(
                      "openai-compatible",
                      Map.of("model", "candidate", "baseUrl", "http://localhost:12345")))
          .isInstanceOf(IOException.class);
      assertThat(closes.get()).isEqualTo(2);
      try (var session = runtime.acquire()) {
        assertThat(session.client()).isSameAs(initial);
      }
      assertThat(runtime.property("spring.ai.model.chat", "unknown")).isEqualTo("unknown");
    }
  }

  @Test
  void shutdownDefersOwnedResourceCloseAndRejectsNewWork() {
    AtomicInteger closes = new AtomicInteger();
    var runtime =
        new LiveModelProviderRuntime(
            client(),
            (key, fallback) -> fallback,
            props -> new LiveModelProviderRuntime.Client(client(), closes::incrementAndGet));
    try (var prepared = runtime.prepare(properties("first", true))) {
      prepared.commit();
    }
    var session = runtime.acquire();
    runtime.close();
    runtime.close();
    assertThat(closes.get()).isZero();
    assertThatThrownBy(runtime::acquire).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> runtime.prepare(properties("second", true)))
        .isInstanceOf(IllegalStateException.class);
    session.close();
    assertThat(closes.get()).isEqualTo(1);
  }

  @Test
  void failedPreparationReleasesTransitionAndCleanupFailureDoesNotUndoActivation() {
    AtomicInteger builds = new AtomicInteger();
    Function<Map<String, Object>, LiveModelProviderRuntime.Client> factory =
        props -> {
          if (builds.getAndIncrement() == 0)
            throw new IllegalArgumentException("Initialization failed");
          return new LiveModelProviderRuntime.Client(
              client(),
              () -> {
                throw new IllegalStateException("Cleanup failure");
              });
        };
    try (var runtime =
        new LiveModelProviderRuntime(client(), (key, fallback) -> fallback, factory)) {
      assertThatThrownBy(() -> runtime.prepare(properties("bad", true)))
          .isInstanceOf(IllegalArgumentException.class);
      try (var first = runtime.prepare(properties("first", true))) {
        first.commit();
      }
      try (var second = runtime.prepare(properties("second", false))) {
        second.commit();
      }
      assertThat(runtime.property("spring.ai.model.chat", "unknown")).isEqualTo("second");
    }
  }
}
