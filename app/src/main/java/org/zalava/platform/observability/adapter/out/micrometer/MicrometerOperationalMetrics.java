package org.zalava.platform.observability.adapter.out.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zalava.platform.observability.application.port.out.OperationalMetrics;

/** Micrometer adapter with SEA-owned cardinality and privacy constraints. */
@Component
public final class MicrometerOperationalMetrics implements OperationalMetrics {
  private static final int MAX_DYNAMIC_VALUES = 20;
  private final MeterRegistry registry;
  private final Set<String> providers = ConcurrentHashMap.newKeySet();
  private final Set<String> tools = ConcurrentHashMap.newKeySet();
  private final Set<String> contextSources = ConcurrentHashMap.newKeySet();

  @Autowired
  public MicrometerOperationalMetrics(ObjectProvider<MeterRegistry> registries) {
    this(registries.getIfAvailable());
  }

  MicrometerOperationalMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void agentRun(String outcome, long durationMillis, List<ContextUse> contextUse) {
    safely(
        () -> {
          count("sea.agent.runs", "outcome", fixed(outcome));
          timer("sea.agent.run.duration", durationMillis, "outcome", fixed(outcome));
          contextUse.forEach(
              use -> {
                String source = bounded(contextSources, use.sourceCategory());
                registry
                    .summary("sea.agent.context.characters", "source", source)
                    .record(Math.max(0, use.charactersUsed()));
              });
        });
  }

  @Override
  public void toolInvocation(String provider, String tool, String outcome, long durationMillis) {
    safely(
        () -> {
          String boundedProvider = bounded(providers, provider);
          String boundedTool = bounded(tools, tool);
          count(
              "sea.provider.tool.invocations",
              "provider",
              boundedProvider,
              "tool",
              boundedTool,
              "outcome",
              fixed(outcome));
          timer(
              "sea.provider.tool.duration",
              durationMillis,
              "provider",
              boundedProvider,
              "tool",
              boundedTool,
              "outcome",
              fixed(outcome));
        });
  }

  @Override
  public void taskExecution(String outcome, String state, long durationMillis) {
    safely(
        () -> {
          count("sea.task.executions", "outcome", fixed(outcome), "state", fixed(state));
          timer(
              "sea.task.execution.duration",
              durationMillis,
              "outcome",
              fixed(outcome),
              "state",
              fixed(state));
        });
  }

  @Override
  public void knowledgeLifecycle(String operation, String outcome) {
    safely(
        () ->
            count(
                "sea.knowledge.lifecycle",
                "operation",
                fixed(operation),
                "outcome",
                fixed(outcome)));
  }

  private void count(String name, String... tags) {
    registry.counter(name, tags).increment();
  }

  private void timer(String name, long durationMillis, String... tags) {
    registry.timer(name, tags).record(java.time.Duration.ofMillis(Math.max(0, durationMillis)));
  }

  private void safely(Runnable operation) {
    if (registry == null) return;
    try {
      operation.run();
    } catch (RuntimeException ignored) {
      /* metrics must never affect SEA use cases */
    }
  }

  private static String fixed(String value) {
    return value == null || !value.matches("[a-z_]+") ? "other" : value;
  }

  private static String bounded(Set<String> known, String value) {
    String normalized = fixed(value);
    if ("other".equals(normalized) || known.contains(normalized)) return normalized;
    if (known.size() >= MAX_DYNAMIC_VALUES) return "other";
    known.add(normalized);
    return normalized;
  }
}
