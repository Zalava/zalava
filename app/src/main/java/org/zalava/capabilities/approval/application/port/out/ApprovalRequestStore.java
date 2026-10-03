package org.zalava.capabilities.approval.application.port.out;

import java.util.List;
import org.zalava.capabilities.approval.ZalavaToolApprovalRequests.Entry;

public interface ApprovalRequestStore {
  List<Entry> load();

  void save(Entry entry);

  void delete(String requestId);
}
