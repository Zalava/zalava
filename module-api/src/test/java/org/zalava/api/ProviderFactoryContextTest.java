package org.zalava.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.api.extensions.tasks.TaskService;

class ProviderFactoryContextTest {
  @Test
  void exposesHostServicesOnlyToTheirAssignedModuleFactory() {
    TaskService taskService = mock(TaskService.class);
    ProviderFactoryContext context =
        new ProviderFactoryContext(
            Map.of(
                "modules",
                Map.of("zalava-module-tasks", Map.of("factories", Map.of("tasks", Map.of())))),
            FactorySecretAccess.none(),
            Map.of(),
            Map.of("zalava-module-tasks", Map.of(TaskService.class, taskService)));

    assertThat(context.forFactory("zalava-module-tasks", "tasks").service(TaskService.class))
        .containsSame(taskService);
    assertThat(context.forFactory("other-module", "other").service(TaskService.class)).isEmpty();
  }

  @Test
  void retainsModuleScopedServicesWhenFactoryHasNoConfiguration() {
    TaskService taskService = mock(TaskService.class);
    ProviderFactoryContext context =
        new ProviderFactoryContext(
            Map.of("modules", Map.of("zalava-module-tasks", Map.of("factories", Map.of()))),
            FactorySecretAccess.none(),
            Map.of(),
            Map.of("zalava-module-tasks", Map.of(TaskService.class, taskService)));

    ProviderFactoryContext factory = context.forFactory("zalava-module-tasks", "tasks");

    assertThat(factory.configuration()).isEmpty();
    assertThat(factory.service(TaskService.class)).containsSame(taskService);
  }
}
