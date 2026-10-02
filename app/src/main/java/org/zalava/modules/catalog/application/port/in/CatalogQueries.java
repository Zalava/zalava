package org.zalava.modules.catalog.application.port.in;

import java.util.List;
import org.zalava.modules.catalog.ModuleReleaseIndex;
import org.zalava.modules.catalog.ModuleReleaseSelection;
import org.zalava.modules.catalog.SourceModuleCatalog;
import org.zalava.modules.catalog.SourceModuleCatalogSearch;

/** Framework-free catalog selection and search entry points. */
public interface CatalogQueries {
  List<SourceModuleCatalogSearch.Result> search(SourceModuleCatalog catalog, String query);

  ModuleReleaseSelection.SelectedRelease selectRelease(
      ModuleReleaseIndex index, String moduleId, String version);
}
