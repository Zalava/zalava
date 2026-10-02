package org.zalava.web.control.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.zalava.web.control.application.port.in.InvocationLogQueries;

public final class InvocationLog implements InvocationLogQueries {
  private static final int MAX_ENTRIES = 50;
  private final ArrayDeque<Entry> entries = new ArrayDeque<>();

  public synchronized void record(Entry entry) {
    entries.addFirst(entry);
    while (entries.size() > MAX_ENTRIES) entries.removeLast();
  }

  @Override
  public synchronized List<Entry> recentEntries() {
    return List.copyOf(new ArrayList<>(entries));
  }
}
