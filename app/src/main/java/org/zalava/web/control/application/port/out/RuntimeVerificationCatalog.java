package org.zalava.web.control.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaVerificationDescriptor;

public interface RuntimeVerificationCatalog {
  List<ZalavaVerificationDescriptor> verifications();

  Optional<ZalavaProvider> findProvider(String providerId);
}
