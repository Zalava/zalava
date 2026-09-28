package org.zalava.catalog;

import java.util.List;

public record SourceModuleCatalog(
    int schemaVersion,
    Repository repository,
    List<MavenRepository> mavenRepositories,
    List<Entry> entries) {

  public SourceModuleCatalog {
    mavenRepositories = List.copyOf(mavenRepositories);
    entries = List.copyOf(entries);
  }

  public record Entry(String moduleId, String path, String sha256, SourceModuleIndex index) {}

  public record Repository(String type, String indexRepository, String indexPath) {}

  public record MavenRepository(String repositoryId, String url) {}
}
