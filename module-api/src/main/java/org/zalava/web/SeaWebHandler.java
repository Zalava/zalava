package org.zalava.web;

@FunctionalInterface
public interface SeaWebHandler {

  SeaWebResponse handle(SeaWebRequest request);
}
