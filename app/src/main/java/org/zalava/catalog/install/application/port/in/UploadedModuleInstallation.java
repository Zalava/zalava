package org.zalava.catalog.install.application.port.in;

import java.io.InputStream;
import org.zalava.catalog.ModuleReleaseInstallRequest;

/** Stages one administrator-supplied module JAR and prepares it for explicit approval. */
public interface UploadedModuleInstallation {
  ModuleReleaseInstallRequest create(Request request);

  record Request(String originalFilename, InputStream content) {}
}
