package org.zalava.catalog.install.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.support.PostgreSqlTestDatabase;

/**
 * Opt-in permission negative for the indexed install control path: an authenticated member and an
 * anonymous caller cannot decide a module-release installation. Screen security is enabled here,
 * unlike the real-network positive lane.
 */
@Tag("indexed-install")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IndexedInstallPermissionNegativeTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountLifecycle accounts;

  @BeforeEach
  void ensureMemberAccount() {
    var member =
        accounts
            .findByLoginName("indexed-install-member")
            .orElseGet(
                () ->
                    accounts.create(
                        "indexed-install-member", "MemberPassword-123", AccountRole.MEMBER));
    if (member.passwordChangeRequired()) {
      accounts.changePassword(member.id(), "MemberPassword-123", "ChangedMemberPassword-123");
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> "indexed-install-admin");
    registry.add("sea.accounts.bootstrap-password", () -> "IndexedInstallPassword-123");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    PostgreSqlTestDatabase.register(registry);
  }

  @Test
  void memberCannotDecideAModuleReleaseInstallation() throws Exception {
    mockMvc
        .perform(
            post("/sea/control/module-release-installations/any-request/allow")
                .with(user("indexed-install-member").roles("MEMBER"))
                .with(csrf()))
        .andExpect(status().isForbidden());
    assertThat(enabledRegistryExists()).isFalse();
  }

  @Test
  void anonymousCannotDecideAModuleReleaseInstallation() throws Exception {
    mockMvc
        .perform(post("/sea/control/module-release-installations/any-request/allow").with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(enabledRegistryExists()).isFalse();
  }

  private static boolean enabledRegistryExists() {
    return Files.exists(WORKSPACE.resolve("source-module-installation/enabled-modules.json"));
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("sea-indexed-install-permission-");
      Files.writeString(workspace.resolve("AGENT.md"), "Indexed install permission workspace.");
      Files.writeString(workspace.resolve("INFO.md"), "Disposable permission workspace.");
      return workspace;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
