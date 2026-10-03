package org.zalava.knowledge.skills.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.skills.domain.SkillActivation;

class FileSystemSkillActivationStoreTest {

  @TempDir Path workspace;

  private final Actor actor = new Actor(AccountId.newId());

  @Test
  void savesFindsAndListsActivationsAcrossReloads() {
    FileSystemSkillActivationStore store = new FileSystemSkillActivationStore(workspace);
    SkillActivation activation =
        SkillActivation.activated(
            actor.accountId().toString(), "test-skill", "1.0.0", "digest", "body", "now");

    store.save(activation);

    FileSystemSkillActivationStore reloaded = new FileSystemSkillActivationStore(workspace);
    assertThat(reloaded.find(actor, "test-skill")).contains(activation);
    assertThat(reloaded.list(actor)).containsExactly(activation);
  }

  @Test
  void keepsActivationsIsolatedPerActor() {
    FileSystemSkillActivationStore store = new FileSystemSkillActivationStore(workspace);
    Actor other = new Actor(AccountId.newId());
    store.save(
        SkillActivation.activated(
            actor.accountId().toString(), "test-skill", "1.0.0", "digest", "body", "now"));

    assertThat(store.find(other, "test-skill")).isEmpty();
    assertThat(store.list(other)).isEmpty();
  }

  @Test
  void skipsUnreadableRecordsInsteadOfFailingTheListing() throws Exception {
    FileSystemSkillActivationStore store = new FileSystemSkillActivationStore(workspace);
    Path directory =
        workspace
            .resolve("users")
            .resolve(actor.accountId().toString())
            .resolve("skill-activations");
    Files.createDirectories(directory);
    Files.writeString(directory.resolve("broken.json"), "{not-json");

    assertThat(store.list(actor)).isEmpty();
  }

  @Test
  void rejectsAnInvalidSkillName() {
    FileSystemSkillActivationStore store = new FileSystemSkillActivationStore(workspace);

    assertThatThrownBy(() -> store.find(actor, "../escape"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid Zalava skill name");
  }
}
