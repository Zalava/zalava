package org.zalava.web.application;

import java.util.Map;

public record RouteInvocation(
    RegisteredWebPage page,
    RegisteredWebRoute route,
    String path,
    Map<String, String> pathVariables) {}
