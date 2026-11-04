package org.zalava.modules.web.application.port.out;

import java.util.List;
import java.util.Optional;

public interface ModulePageAccessStore {
  record PageKey(String moduleId, String pageId) {}

  List<PageKey> savedPages();

  boolean memberDefault(String moduleId, String pageId);

  Optional<Boolean> override(String accountId, String moduleId, String pageId);

  void setDefault(String administratorId, String moduleId, String pageId, boolean allowed);

  void setOverride(
      String administratorId, String accountId, String moduleId, String pageId, String decision);
}
