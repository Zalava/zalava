package org.zalava.web;

@FunctionalInterface
public interface ZalavaWebHandler {

  ZalavaWebResponse handle(ZalavaWebRequest request);
}
