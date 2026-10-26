package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.zalava.api.InvocationContext;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.support.PostgreSqlTestDatabase;
import org.zalava.support.RestartableZalavaApplicationContext;

@Isolated("Restarts host contexts and their process-wide logging configuration")
class MemberToolAccessRestartTest {
  @Test
  void grantAndRevocationSurviveRealHostContextRestarts() throws Exception {
    var workspace = Files.createTempDirectory("member-grant-restart-");
    var properties =
        Map.of(
            "spring.datasource.url",
            PostgreSqlTestDatabase.newJdbcUrl(),
            "spring.ai.model.chat",
            "unknown",
            "agent.onboarding.completed",
            "true",
            "zalava.accounts.password-encoder-strength",
            "4");
    String memberId, administratorId;
    var key =
        new ToolKey(
            MemberToolAccessFixture.MODULE, MemberToolAccessFixture.PROVIDER, "current_time");
    try (var first =
        RestartableZalavaApplicationContext.start(
            workspace, properties, MemberToolAccessFixture.class)) {
      var accounts = first.getBean(AccountLifecycle.class);
      administratorId =
          accounts
              .create("restart-admin", "RestartAdminPassword-123", AccountRole.ADMIN)
              .id()
              .toString();
      memberId =
          accounts
              .create("restart-member", "RestartMemberPassword-123", AccountRole.MEMBER)
              .id()
              .toString();
      first.getBean(MemberToolAccess.class).decide(administratorId, memberId, key, true);
    }
    try (var second =
        RestartableZalavaApplicationContext.start(
            workspace, properties, MemberToolAccessFixture.class)) {
      assertThat(second.getBean(MemberToolAccess.class).allowed(memberId, key)).isTrue();
      assertThat(
              second
                  .getBean(ProviderToolOperations.class)
                  .invoke(
                      ProviderToolOperations.ToolInvocationCommand.operator(
                          key.providerId(),
                          key.toolName(),
                          "{}",
                          new InvocationContext(memberId, false, Map.of("accountRole", "MEMBER"))))
                  .status())
          .isEqualTo(ProviderToolOperations.ToolInvocationStatus.EXECUTED);
      second.getBean(MemberToolAccess.class).decide(administratorId, memberId, key, false);
    }
    try (var third =
        RestartableZalavaApplicationContext.start(
            workspace, properties, MemberToolAccessFixture.class)) {
      assertThat(third.getBean(MemberToolAccess.class).allowed(memberId, key)).isFalse();
      assertThatThrownBy(
              () ->
                  third
                      .getBean(ProviderToolOperations.class)
                      .invoke(
                          ProviderToolOperations.ToolInvocationCommand.operator(
                              key.providerId(),
                              key.toolName(),
                              "{}",
                              new InvocationContext(
                                  memberId, false, Map.of("accountRole", "MEMBER")))))
          .hasMessageContaining("denied");
    }
  }
}
