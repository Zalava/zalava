package org.zalava.modules.web.application.port.in;

import java.util.List;
import java.util.Optional;
import org.zalava.modules.web.application.RegisteredWebPage;
import org.zalava.modules.web.application.RouteInvocation;

public interface WebExtensionRoutes {

  List<RegisteredWebPage> pages();

  Optional<RouteInvocation> resolve(String method, String moduleId, String pageId, String path);
}
