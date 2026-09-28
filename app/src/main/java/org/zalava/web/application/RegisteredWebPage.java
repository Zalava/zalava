package org.zalava.web.application;

import java.util.List;

public record RegisteredWebPage(
    String moduleId,
    String moduleDisplayName,
    String extensionId,
    String pageId,
    String title,
    String description,
    String navSection,
    List<RegisteredWebRoute> routes)
    implements Comparable<RegisteredWebPage> {

  public String path() {
    return "/apps/" + moduleId + "/" + pageId;
  }

  @Override
  public int compareTo(RegisteredWebPage other) {
    int sectionComparison = navSection.compareTo(other.navSection);
    if (sectionComparison != 0) {
      return sectionComparison;
    }
    int moduleComparison = moduleDisplayName.compareTo(other.moduleDisplayName);
    return moduleComparison != 0 ? moduleComparison : title.compareTo(other.title);
  }
}
