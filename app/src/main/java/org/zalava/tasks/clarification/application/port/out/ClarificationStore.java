package org.zalava.tasks.clarification.application.port.out;

import java.util.List;
import org.zalava.tasks.clarification.domain.ClarificationRequest;

public interface ClarificationStore {
  List<ClarificationRequest> load();

  void save(ClarificationRequest request);

  void delete(String requestId);
}
