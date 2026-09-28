package org.zalava.catalog.application.port.in;

import java.util.List;
import org.zalava.catalog.ModuleReleaseIndex;
import org.zalava.catalog.ModuleReleaseSelection;
import org.zalava.catalog.SourceModuleCatalog;
import org.zalava.catalog.SourceModuleCatalogSearch;

/** Framework-free catalog selection and search entry points. */
public interface CatalogQueries {
  List<SourceModuleCatalogSearch.Result> search(SourceModuleCatalog catalog, String query);

  ModuleReleaseSelection.SelectedRelease selectRelease(
      ModuleReleaseIndex index, String moduleId, String version);
}
