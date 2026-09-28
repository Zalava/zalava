package org.zalava.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillActivationTest {

  @Test
  void activatesWithHistoryAndRemainsActive() {
    SkillActivation activation =
        SkillActivation.activated("actor", "test-skill", "1.0.0", "digest", "body", "now");

    assertThat(activation.active()).isTrue();
    assertThat(activation.state()).isEqualTo(SkillActivationState.ACTIVE);
    assertThat(activation.activatedAt()).isEqualTo("now");
    assertThat(activation.updatedAt()).isEqualTo("now");
    assertThat(activation.history())
        .extracting(SkillActivation.Event::type)
        .containsExactly("activated");
  }

  @Test
  void refreshUpdatesVersionAndContentWhileKeepingTheFirstActivationTime() {
    SkillActivation activation =
        SkillActivation.activated("actor", "test-skill", "1.0.0", "digest", "body", "first");

    SkillActivation refreshed = activation.refreshed("2.0.0", "new", "new body", "later");

    assertThat(refreshed.version()).isEqualTo("2.0.0");
    assertThat(refreshed.activatedAt()).isEqualTo("first");
    assertThat(refreshed.updatedAt()).isEqualTo("later");
    assertThat(refreshed.history())
        .extracting(SkillActivation.Event::type)
        .containsExactly("activated", "refreshed");
  }

  @Test
  void deactivateRollsBackAndIsIdempotent() {
    SkillActivation activation =
        SkillActivation.activated("actor", "test-skill", "1.0.0", "digest", "body", "now");

    SkillActivation deactivated = activation.deactivated("later");

    assertThat(deactivated.active()).isFalse();
    assertThat(deactivated.state()).isEqualTo(SkillActivationState.DEACTIVATED);
    assertThat(deactivated.deactivated("again")).isSameAs(deactivated);
  }

  @Test
  void reactivationRestoresAnActiveSelection() {
    SkillActivation deactivated =
        SkillActivation.activated("actor", "test-skill", "1.0.0", "digest", "body", "now")
            .deactivated("later");

    SkillActivation reactivated = deactivated.reactivated("1.0.0", "digest", "body", "again");

    assertThat(reactivated.active()).isTrue();
    assertThat(reactivated.history())
        .extracting(SkillActivation.Event::type)
        .containsExactly("activated", "deactivated", "reactivated");
  }

  @Test
  void rejectsMissingRequiredFields() {
    assertThatThrownBy(
            () ->
                new SkillActivation(
                    "", "s", "1.0.0", "d", "b", SkillActivationState.ACTIVE, "a", "u", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("actorId must not be blank");
    assertThatThrownBy(
            () -> new SkillActivation("a", "s", "1.0.0", "d", "b", null, "a", "u", List.of()))
        .isInstanceOf(NullPointerException.class);
  }
}
