package org.zalava.approval.application.port.out;

import java.util.List;
import org.zalava.approval.SeaToolApprovalRequests.Entry;

public interface ApprovalRequestStore {
  List<Entry> load();

  void save(Entry entry);

  void delete(String requestId);
}
