package org.zalava.api.extensions.web;

@FunctionalInterface
public interface ZalavaWebHandler {

  ZalavaWebResponse handle(ZalavaWebRequest request);
}
