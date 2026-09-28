package org.zalava.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.accounts.application.ActorExecutionContext;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.control.application.AdministratorControlAuthorization;
import org.zalava.support.SecureSeaComponentTest;

@SecureSeaComponentTest
@ResourceLock("secure-component-runtime")
@TestPropertySource(properties = "sea.test.context=account-security")
class AccountSecurityComponentTest {
  @Autowired MockMvc mockMvc;
  @Autowired FilterChainProxy securityFilterChain;
  @Autowired AccountLifecycle accounts;
  @Autowired ActorExecutionContext actorExecution;
  @Autowired LocalArtifactModuleInstallation localArtifactInstallations;
  @Autowired AdministratorControlAuthorization controlAuthorization;

  @BeforeEach
  void createAdministratorFixture() {
    accounts
        .findByLoginName("account-security-admin")
        .orElseGet(
            () ->
                accounts.create(
                    "account-security-admin", "TestBootstrapPassword-123", AccountRole.ADMIN));
  }

  @Test
  void controlRejectsAnonymousAndMemberRequests() throws Exception {
    mockMvc.perform(get("/sea/control")).andExpect(status().is3xxRedirection());
    var member = accounts.create("member", "MemberPassword-123", AccountRole.MEMBER);
    accounts.changePassword(member.id(), "MemberPassword-123", "ChangedMemberPassword-123");
    mockMvc
        .perform(get("/sea/control").with(user("member").roles("MEMBER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/sea/control/metrics").with(user("member").roles("MEMBER")))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "account-security-admin", roles = "ADMIN")
  void csrfProtectsPasswordChange() throws Exception {
    mockMvc
        .perform(
            post("/account/password")
                .param("currentPassword", "TestBootstrapPassword-123")
                .param("replacementPassword", "NewPassword-1234"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/account/password")
                .with(csrf())
                .param("currentPassword", "TestBootstrapPassword-123")
                .param("replacementPassword", "NewPassword-1234"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void moduleLifecyclePortsRequireAnAdministratorAndAuditTheTrustedActor() {
    var member = accounts.create("module-member", "MemberPassword-123", AccountRole.MEMBER);
    accounts.changePassword(member.id(), "MemberPassword-123", "ChangedMemberPassword-123");

    assertThatThrownBy(
            () ->
                actorExecution.call(
                    new Actor(member.id()),
                    AccountRole.MEMBER,
                    () -> localArtifactInstallations.recent(1)))
        .isInstanceOf(AccessDeniedException.class);

    var admin = accounts.findByLoginName("account-security-admin").orElseThrow();
    actorExecution.call(
        new Actor(admin.id()), AccountRole.ADMIN, () -> localArtifactInstallations.recent(1));

    assertThat(controlAuthorization.recentEntries())
        .anySatisfy(
            entry -> {
              assertThat(entry.actorId()).isEqualTo(admin.id().toString());
              assertThat(entry.role()).isEqualTo(AccountRole.ADMIN);
              assertThat(entry.target()).isEqualTo("module-local-artifact:recent");
            });
  }

  @Test
  void configuresCookieCsrfRepositoryForSpa() throws Exception {
    CsrfFilter csrfFilter =
        securityFilterChain.getFilterChains().stream()
            .flatMap(chain -> chain.getFilters().stream())
            .filter(CsrfFilter.class::isInstance)
            .map(CsrfFilter.class::cast)
            .findFirst()
            .orElseThrow();
    Field repository = CsrfFilter.class.getDeclaredField("tokenRepository");
    repository.setAccessible(true);

    assertThat(repository.get(csrfFilter)).isInstanceOf(CookieCsrfTokenRepository.class);
    Field requestHandler = CsrfFilter.class.getDeclaredField("requestHandler");
    requestHandler.setAccessible(true);
    assertThat(requestHandler.get(csrfFilter).getClass().getSimpleName())
        .isEqualTo("SpaCsrfTokenRequestHandler");
  }

  @Test
  void acceptsRawSpaCsrfCookieAndHeader() throws Exception {
    var repository = new CookieCsrfTokenRepository();
    var response = new MockHttpServletResponse();
    var token = repository.generateToken(new MockHttpServletRequest());
    repository.saveToken(token, new MockHttpServletRequest(), response);

    var request =
        new MockHttpServletRequest(
            "POST", "/sea/control/module-release-installations/catalog/refresh");
    request.setCookies(response.getCookie("XSRF-TOKEN"));
    request.addHeader("X-XSRF-TOKEN", token.getToken());
    var continued = new AtomicBoolean();

    csrfFilter()
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (ignoredRequest, ignoredResponse) -> continued.set(true));

    assertThat(continued).isTrue();
  }

  private CsrfFilter csrfFilter() {
    return securityFilterChain.getFilterChains().stream()
        .flatMap(chain -> chain.getFilters().stream())
        .filter(CsrfFilter.class::isInstance)
        .map(CsrfFilter.class::cast)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void administratorManagesAccountsThroughCsrfProtectedHtmlWhileMembersAreDenied()
      throws Exception {
    var administrator = accounts.create("accounts-admin", "AdminPassword-123", AccountRole.ADMIN);
    accounts.changePassword(administrator.id(), "AdminPassword-123", "ChangedAdminPassword-123");
    var member = accounts.create("accounts-member", "MemberPassword-123", AccountRole.MEMBER);
    accounts.changePassword(member.id(), "MemberPassword-123", "ChangedMemberPassword-123");

    mockMvc
        .perform(get("/sea/accounts").with(user("accounts-member").roles("MEMBER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/sea/accounts").with(user("accounts-admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Managed accounts")));
    mockMvc
        .perform(
            post("/sea/accounts/create")
                .with(user("accounts-admin").roles("ADMIN"))
                .param("loginName", "managed-member")
                .param("temporaryPassword", "ManagedPassword-123")
                .param("role", "MEMBER"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/sea/accounts/create")
                .with(user("accounts-admin").roles("ADMIN"))
                .with(csrf())
                .param("loginName", "managed-member")
                .param("temporaryPassword", "ManagedPassword-123")
                .param("role", "MEMBER"))
        .andExpect(status().is3xxRedirection());

    var managed = accounts.findByLoginName("managed-member").orElseThrow();
    assertThat(managed.enabled()).isTrue();
    assertThat(managed.passwordChangeRequired()).isTrue();

    mockMvc
        .perform(
            post("/sea/accounts/password")
                .with(user("accounts-admin").roles("ADMIN"))
                .with(csrf())
                .param("accountId", managed.id().value().toString())
                .param("temporaryPassword", "ResetPassword-123"))
        .andExpect(status().is3xxRedirection());
    assertThat(accounts.findByLoginName("managed-member").orElseThrow().passwordChangeRequired())
        .isTrue();
    mockMvc
        .perform(get("/dashboard").with(user("managed-member").roles("MEMBER")))
        .andExpect(status().is3xxRedirection());
    mockMvc
        .perform(
            post("/account/password")
                .with(user("managed-member").roles("MEMBER"))
                .with(csrf())
                .param("currentPassword", "ResetPassword-123")
                .param("replacementPassword", "ChangedManagedPassword-123"))
        .andExpect(status().is3xxRedirection());
    assertThat(accounts.findByLoginName("managed-member").orElseThrow().passwordChangeRequired())
        .isFalse();
    mockMvc
        .perform(
            post("/sea/accounts/role")
                .with(user("accounts-admin").roles("ADMIN"))
                .with(csrf())
                .param("accountId", managed.id().value().toString())
                .param("role", "ADMIN"))
        .andExpect(status().is3xxRedirection());
    assertThat(accounts.findByLoginName("managed-member").orElseThrow().role())
        .isEqualTo(AccountRole.ADMIN);
    mockMvc
        .perform(
            post("/sea/accounts/enabled")
                .with(user("accounts-admin").roles("ADMIN"))
                .with(csrf())
                .param("accountId", managed.id().value().toString())
                .param("enabled", "false"))
        .andExpect(status().is3xxRedirection());
    assertThat(accounts.findByLoginName("managed-member").orElseThrow().enabled()).isFalse();
  }
}
