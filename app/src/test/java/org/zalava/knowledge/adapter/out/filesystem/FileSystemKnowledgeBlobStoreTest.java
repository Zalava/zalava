package org.zalava.knowledge.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.zalava.knowledge.domain.KnowledgeSourceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemKnowledgeBlobStoreTest {
  @TempDir Path root;

  @Test
  void storesEachSourceIndependentlyEvenWhenTheContentHashMatches() {
    var store = new FileSystemKnowledgeBlobStore(root);
    byte[] content = "same content".getBytes(StandardCharsets.UTF_8);
    var first = KnowledgeSourceId.create();
    var second = KnowledgeSourceId.create();

    var firstReceipt = store.write(first, content);
    var secondReceipt = store.write(second, content);

    assertThat(firstReceipt.sha256()).isEqualTo(secondReceipt.sha256());
    assertThat(store.read(first)).contains(content);
    assertThat(store.read(second)).contains(content);
  }

  @Test
  void deletesOriginalAndRecoversAnIncompleteWriteForTheSameSource() throws Exception {
    var store = new FileSystemKnowledgeBlobStore(root);
    var source = KnowledgeSourceId.create();
    store.write(source, "content".getBytes(StandardCharsets.UTF_8));
    Path pending = root.resolve("knowledge/originals/." + source.value() + "-left.pending");
    Files.writeString(pending, "incomplete");

    store.delete(source);

    assertThat(store.read(source)).isEmpty();
    assertThat(Files.exists(pending)).isFalse();
  }

  @Test
  void reloadsAnAtomicallyWrittenOriginalAfterStoreRestart() {
    var source = KnowledgeSourceId.create();
    new FileSystemKnowledgeBlobStore(root)
        .write(source, "restart-safe".getBytes(StandardCharsets.UTF_8));

    assertThat(new FileSystemKnowledgeBlobStore(root).read(source))
        .contains("restart-safe".getBytes(StandardCharsets.UTF_8));
  }
}
