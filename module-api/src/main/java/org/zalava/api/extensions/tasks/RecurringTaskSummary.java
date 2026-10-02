package org.zalava.api.extensions.tasks;

/** Immutable recurring-task projection safe for external modules. */
public record RecurringTaskSummary(String id, String name, String description) {}
