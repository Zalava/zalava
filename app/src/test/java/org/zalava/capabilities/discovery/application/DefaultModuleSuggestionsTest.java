package org.zalava.capabilities.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions.Module;

class DefaultModuleSuggestionsTest {
  @Test
  void keepsReadableMetadataAndNormalizesQueryWithoutGrantingAuthority() {
    var module =
        new Module(
            "zalava-module-tika", "Tika content extractor", "Read PDF documents.\nExtract text.");
    var result = new DefaultModuleSuggestions(() -> List.of(module)).suggest("please READ pdf");
    assertThat(result.status()).isEqualTo(ModuleSuggestions.Status.MATCH);
    assertThat(result.modules().getFirst().displayName()).isEqualTo("Tika content extractor");
    assertThat(result.modules().getFirst().description())
        .isEqualTo("Read PDF documents. Extract text.");
  }

  @Test
  void boundsCandidatesAndMetadataDeterministically() {
    var modules =
        IntStream.range(0, 150)
            .mapToObj(
                i ->
                    new Module(
                        "module-" + String.format("%03d", i),
                        "Task " + "a".repeat(200),
                        "Job " + "b".repeat(1800)))
            .toList();
    var result = new DefaultModuleSuggestions(() -> modules).suggest("job task");
    assertThat(result.modules()).hasSize(5);
    assertThat(result.modules().getFirst().moduleId()).isEqualTo("module-000");
    assertThat(result.modules().getFirst().displayName()).hasSize(160);
    assertThat(result.modules().getFirst().description()).hasSize(1500);
  }

  @Test
  void validatesInputsAndTreatsInvalidCatalogAsUnavailable() {
    var service =
        new DefaultModuleSuggestions(() -> List.of(new Module("invalid/id", "Jobs", "Tasks")));
    assertThat(service.suggest("jobs").status()).isEqualTo(ModuleSuggestions.Status.UNAVAILABLE);
    for (String input : new String[] {null, " ", "a".repeat(2001)})
      assertThatThrownBy(() -> service.suggest(input)).isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new DefaultModuleSuggestions(() -> List.of(new Module("module", null, null)))
                .suggest("pollen")
                .status())
        .isEqualTo(ModuleSuggestions.Status.NO_MATCH);
    assertThat(new DefaultModuleSuggestions(() -> List.of()).suggest("can you please").status())
        .isEqualTo(ModuleSuggestions.Status.NO_MATCH);
  }
}
