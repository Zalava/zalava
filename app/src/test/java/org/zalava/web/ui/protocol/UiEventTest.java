package org.zalava.web.ui.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiEventTest {
  @Test
  void completedChatEventRetainsAnImmutableSnapshotOfJobReferences() {
    var jobIds = new ArrayList<>(List.of("job-1"));
    var event =
        new UiEvent.ChatCompleted(UiCommandDecoder.VERSION, "conversation-1", "Done", jobIds);
    jobIds.add("job-2");

    assertThat(event.jobIds()).containsExactly("job-1");
    assertThat(event).isInstanceOf(UiEvent.class);
  }
}
