package org.zalava.modules.catalog.application;

import java.util.List;
import org.zalava.modules.catalog.ModuleReleaseIndex;
import org.zalava.modules.catalog.ModuleReleaseSelection;
import org.zalava.modules.catalog.SourceModuleCatalog;
import org.zalava.modules.catalog.SourceModuleCatalogSearch;
import org.zalava.modules.catalog.application.port.in.CatalogQueries;

public final class DefaultCatalogQueries implements CatalogQueries {
  private final SourceModuleCatalogSearch search = new SourceModuleCatalogSearch();
  private final ModuleReleaseSelection selection = new ModuleReleaseSelection();

  @Override
  public List<SourceModuleCatalogSearch.Result> search(SourceModuleCatalog catalog, String query) {
    return search.search(catalog, query);
  }

  @Override
  public ModuleReleaseSelection.SelectedRelease selectRelease(
      ModuleReleaseIndex index, String moduleId, String version) {
    return selection.select(index, moduleId, version);
  }
}
