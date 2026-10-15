package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.zalava.knowledge.domain.KnowledgeVisibility;

class LibraryPresentationTest {
  @Test
  void fileInformationUsesReadableLabelsAndSizes() {
    assertThat(LibraryPresentation.fileType("text/plain")).isEqualTo("Text document");
    assertThat(LibraryPresentation.fileType("text/markdown")).isEqualTo("Text document");
    assertThat(LibraryPresentation.fileType("text/html")).isEqualTo("HTML document");
    assertThat(LibraryPresentation.fileType("application/pdf")).isEqualTo("PDF document");
    assertThat(
            LibraryPresentation.fileType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        .isEqualTo("Word document");
    assertThat(LibraryPresentation.fileType("custom/type")).isEqualTo("custom/type");
    assertThat(LibraryPresentation.fileSize(1)).isEqualTo("1 byte");
    assertThat(LibraryPresentation.fileSize(0)).isEqualTo("0 bytes");
    assertThat(LibraryPresentation.fileSize(1024)).isEqualTo("1.0 KB");
    assertThat(LibraryPresentation.fileSize(1048576)).isEqualTo("1.0 MB");
    assertThat(LibraryPresentation.visibility(KnowledgeVisibility.PRIVATE))
        .isEqualTo("Private to you");
    assertThat(LibraryPresentation.visibility(KnowledgeVisibility.GROUP_SHARED))
        .isEqualTo("Shared with workspace");
  }
}
