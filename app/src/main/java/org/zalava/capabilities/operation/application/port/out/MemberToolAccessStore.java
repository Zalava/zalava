package org.zalava.capabilities.operation.application.port.out;

import java.time.Instant;
import java.util.List;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.Decision;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;

public interface MemberToolAccessStore {
  boolean defaultAllowed(ToolKey key);

  List<Decision> defaults();

  void defaultAccess(String administratorId, ToolKey key, boolean allowed, Instant at);

  void inherit(String administratorId, String memberId, ToolKey key, Instant at);

  boolean allowed(String memberId, ToolKey key);

  List<Decision> decisions(String memberId);

  void decide(String administratorId, String memberId, ToolKey key, boolean allowed, Instant at);
}
