package org.zalava.capabilities.operation.application.port.out;

import java.time.Instant;
import java.util.List;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.Decision;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;

public interface MemberToolAccessStore {
  boolean allowed(String memberId, ToolKey key);

  List<Decision> decisions(String memberId);

  void decide(String administratorId, String memberId, ToolKey key, boolean allowed, Instant at);
}
