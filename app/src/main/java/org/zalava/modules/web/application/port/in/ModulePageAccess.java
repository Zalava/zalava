package org.zalava.modules.web.application.port.in;

import java.util.List;
import org.zalava.modules.web.application.RegisteredWebPage;

public interface ModulePageAccess {
  boolean allowed(String accountId, String moduleId, String pageId);

  List<RegisteredWebPage> pages(String administratorId);

  boolean memberDefault(String administratorId, String moduleId, String pageId);

  String override(String administratorId, String accountId, String moduleId, String pageId);

  void setDefault(String administratorId, String moduleId, String pageId, boolean allowed);

  void setOverride(
      String administratorId, String accountId, String moduleId, String pageId, String decision);
}
