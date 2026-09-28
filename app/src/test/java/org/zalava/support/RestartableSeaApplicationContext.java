package org.zalava.support;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.zalava.SeaApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts an isolated SEA application context against the shared PostgreSQL test database. */
public final class RestartableSeaApplicationContext {
  private RestartableSeaApplicationContext() {}

  public static ConfigurableApplicationContext start(Path workspace) {
    return start(workspace, Map.of());
  }

  public static ConfigurableApplicationContext start(
      Path workspace, Map<String, String> additionalProperties) {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("agent.workspace", workspace.toUri().toString());
    properties.put("sea.accounts.security-enabled", "false");
    properties.put("sea.accounts.bootstrap-login", "knowledge-admin");
    properties.put("sea.accounts.bootstrap-password", "KnowledgePassword-123");
    properties.put("jobrunr.background-job-server.enabled", "false");
    properties.put("jobrunr.dashboard.enabled", "false");
    properties.put("server.port", "0");
    properties.put("spring.datasource.url", PostgreSqlTestDatabase.newJdbcUrl());
    properties.putAll(additionalProperties);
    return new SpringApplicationBuilder(SeaApplication.class)
        .initializers(
            context -> TestPropertyValues.of(properties).applyTo(context.getEnvironment()))
        .run();
  }
}
