package org.zalava.tasks.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;

class ActorFileSystemTaskStoreTest {
  @TempDir Path workspace;

  @Test
  void persistsOnlyOpaqueReferencesInsideTheOwningActorPartition() {
    Actor first = new Actor(AccountId.newId());
    Actor second = new Actor(AccountId.newId());
    ActorTaskReference reference = ActorTaskReference.newReference();
    var store = new ActorFileSystemTaskStore(workspace);

    Task saved = store.save(first, reference, Task.newTask("private task", "only first"));

    assertThat(saved.getId()).isEqualTo(reference.value());
    assertThat(store.list(first)).containsExactly(reference);
    assertThat(store.get(first, reference).getName()).isEqualTo("private task");
    assertThat(store.list(second)).isEmpty();
    assertThatThrownBy(() -> store.get(second, reference))
        .isInstanceOf(TaskNotFoundException.class);
    assertThat(reference.value()).doesNotContain("/", "\\", "tasks");
  }
}
