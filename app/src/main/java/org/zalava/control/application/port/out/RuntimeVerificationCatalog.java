package org.zalava.control.application.port.out;

import java.util.List;
import java.util.Optional;
import org.zalava.SeaProvider;
import org.zalava.SeaVerificationDescriptor;

public interface RuntimeVerificationCatalog {
  List<SeaVerificationDescriptor> verifications();

  Optional<SeaProvider> findProvider(String providerId);
}
