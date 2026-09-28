package org.zalava.knowledge.domain;

import java.util.UUID;

public record KnowledgeEvidence(
    UUID sourceId,
    long derivationVersion,
    String displayName,
    String contentType,
    String excerpt,
    boolean truncated) {}
