package org.zalava.capabilities.operation.application.port.out;

import java.util.Map;

/** Decodes the transport representation before policy and provider execution. */
public interface ToolArgumentDecoder {
  Map<String, Object> decode(String source);
}
