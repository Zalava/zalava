package org.zalava.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.skills.application.port.out.SkillCatalog;
import org.zalava.skills.domain.SkillDescriptor;
import org.zalava.skills.domain.SkillProvenance;
import org.zalava.skills.domain.SkillStatus;
import org.zalava.skills.domain.SkillVisibility;
import org.junit.jupiter.api.Test;

class DefaultSkillDiscoveryTest {

  @Test
  void deduplicatesByNameKeepingTheHighestVersionAndOrderingByName() {
    SkillCatalog local =
        () ->
            List.of(
                descriptor("beta", "1.0.0", SkillVisibility.ALL),
                descriptor("alpha", "1.0.0", SkillVisibility.ALL),
                descriptor("alpha", "2.0.0", SkillVisibility.ALL));
    DefaultSkillDiscovery discovery = new DefaultSkillDiscovery(local, () -> List.of(), Set.of());

    assertThat(discovery.installed(AccountRole.MEMBER))
        .extracting(SkillDescriptor::name, SkillDescriptor::version)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("alpha", "2.0.0"),
            org.assertj.core.groups.Tuple.tuple("beta", "1.0.0"));
  }

  @Test
  void hidesAdminOnlySkillsFromNonAdministrators() {
    SkillCatalog local =
        () ->
            List.of(
                descriptor("public-skill", "1.0.0", SkillVisibility.ALL),
                descriptor("admin-skill", "1.0.0", SkillVisibility.ADMIN));
    DefaultSkillDiscovery discovery = new DefaultSkillDiscovery(local, () -> List.of(), Set.of());

    assertThat(discovery.installed(AccountRole.MEMBER))
        .extracting(SkillDescriptor::name)
        .containsExactly("public-skill");
    assertThat(discovery.installed(AccountRole.ADMIN))
        .extracting(SkillDescriptor::name)
        .containsExactly("admin-skill", "public-skill");
    assertThat(discovery.find(AccountRole.MEMBER, "admin-skill")).isEmpty();
    assertThat(discovery.find(AccountRole.ADMIN, "admin-skill")).isPresent();
  }

  @Test
  void activeStateComesFromConfiguredNames() {
    SkillCatalog local = () -> List.of(descriptor("enabled", "1.0.0", SkillVisibility.ALL));
    DefaultSkillDiscovery discovery =
        new DefaultSkillDiscovery(local, () -> List.of(), Set.of("enabled"));

    assertThat(discovery.active(AccountRole.MEMBER))
        .singleElement()
        .satisfies(
            skill -> {
              assertThat(skill.name()).isEqualTo("enabled");
              assertThat(skill.status()).isEqualTo(SkillStatus.ACTIVE);
            });
    assertThat(discovery.installed(AccountRole.MEMBER))
        .singleElement()
        .satisfies(skill -> assertThat(skill.status()).isEqualTo(SkillStatus.INSTALLED));
  }

  @Test
  void remoteCatalogueIsMergedIntoDiscovery() {
    SkillCatalog remote = () -> List.of(descriptor("remote-skill", "1.0.0", SkillVisibility.ALL));
    DefaultSkillDiscovery discovery = new DefaultSkillDiscovery(() -> List.of(), remote, Set.of());

    assertThat(discovery.remote(AccountRole.MEMBER))
        .singleElement()
        .satisfies(skill -> assertThat(skill.status()).isEqualTo(SkillStatus.REMOTE));
    assertThat(discovery.find(AccountRole.MEMBER, "remote-skill")).isPresent();
  }

  @Test
  void searchIsBoundedAndMatchesNameDescriptionAndCapabilities() {
    SkillCatalog local =
        () ->
            List.of(
                descriptor("shopping-helper", "1.0.0", SkillVisibility.ALL),
                descriptor("weather", "1.0.0", SkillVisibility.ALL));
    DefaultSkillDiscovery discovery =
        new DefaultSkillDiscovery(local, () -> List.of(), Set.of(), 1);

    assertThat(discovery.search(AccountRole.MEMBER, "shopping", 10)).hasSize(1);
    assertThat(discovery.search(AccountRole.MEMBER, "shopping", 10))
        .extracting(SkillDescriptor::name)
        .containsExactly("shopping-helper");
    assertThat(discovery.search(AccountRole.MEMBER, "unmatched", 10)).isEmpty();
    assertThat(discovery.search(AccountRole.MEMBER, null, 10)).hasSize(1);
    assertThatThrownBy(() -> discovery.search(AccountRole.MEMBER, "x", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void comparesVersionsNumerically() {
    assertThat(DefaultSkillDiscovery.compareVersions("2.0.0", "10.0.0")).isNegative();
    assertThat(DefaultSkillDiscovery.compareVersions("1.10.0", "1.9.0")).isPositive();
    assertThat(DefaultSkillDiscovery.compareVersions("1.0.0", "1.0.0")).isZero();
  }

  private static SkillDescriptor descriptor(
      String name, String version, SkillVisibility visibility) {
    return new SkillDescriptor(
        name,
        version,
        "Description for " + name,
        List.of("capability-" + name),
        List.of("tool-" + name),
        List.of(),
        List.of(),
        null,
        visibility,
        SkillProvenance.local(name),
        SkillStatus.INSTALLED);
  }
}
