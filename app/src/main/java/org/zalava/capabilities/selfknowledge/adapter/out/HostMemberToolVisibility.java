package org.zalava.capabilities.selfknowledge.adapter.out;

import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.capabilities.selfknowledge.application.port.out.MemberToolVisibility;

/** Reuses the same eligibility and per-account grant used by tool discovery. */
public final class HostMemberToolVisibility implements MemberToolVisibility {
  private final MemberToolAccess access;

  public HostMemberToolVisibility(MemberToolAccess access) {
    this.access = access;
  }

  @Override
  public boolean allowed(String actor, ZalavaProvider provider, ZalavaToolDescriptor tool) {
    return access.canUse(
        actor,
        new ToolKey(
            provider.descriptor().moduleId(), provider.descriptor().providerId(), tool.name()));
  }
}
