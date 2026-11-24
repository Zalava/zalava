package org.zalava.capabilities.selfknowledge.application.port.out;

import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;

/** Projects the host's effective member-tool policy without granting invocation authority. */
public interface MemberToolVisibility {
  boolean allowed(String accountId, ZalavaProvider provider, ZalavaToolDescriptor tool);
}
