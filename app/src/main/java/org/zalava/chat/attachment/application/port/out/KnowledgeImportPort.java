package org.zalava.chat.attachment.application.port.out;

import org.zalava.accounts.domain.Actor;

/** Requests a durable, owner-scoped knowledge import without exposing the knowledge internals. */
public interface KnowledgeImportPort {
  ImportedSource importSource(Actor actor, String displayName, String contentType, byte[] content);

  record ImportedSource(String sourceId) {}
}
