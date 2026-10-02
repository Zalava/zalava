package org.zalava.web.control.adapter.in.actuator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import org.zalava.modules.runtime.SeaRuntime;

@Component
@EnableConfigurationProperties(ProductionReleaseProperties.class)
public class ProductionRuntimeInfoContributor implements InfoContributor {

  private final SeaRuntime seaRuntime;
  private final ProductionReleaseProperties release;
  private final ObjectProvider<BuildProperties> buildProperties;

  public ProductionRuntimeInfoContributor(
      SeaRuntime seaRuntime,
      ProductionReleaseProperties release,
      ObjectProvider<BuildProperties> buildProperties) {
    this.seaRuntime = seaRuntime;
    this.release = release;
    this.buildProperties = buildProperties;
  }

  @Override
  public void contribute(Info.Builder builder) {
    builder.withDetail(
        "sea",
        Map.of(
            "coreVersion", coreVersion(),
            "releaseImage", release.image(),
            "releaseRevision", release.revision(),
            "modules", modules()));
  }

  private String coreVersion() {
    BuildProperties build = buildProperties.getIfAvailable();
    return build == null ? "unknown" : build.getVersion();
  }

  private List<Map<String, String>> modules() {
    return seaRuntime.modules().stream()
        .map(module -> module.descriptor())
        .sorted(Comparator.comparing(descriptor -> descriptor.moduleId()))
        .map(
            descriptor ->
                Map.of(
                    "id", descriptor.moduleId(),
                    "name", descriptor.displayName(),
                    "version", descriptor.version()))
        .toList();
  }
}
