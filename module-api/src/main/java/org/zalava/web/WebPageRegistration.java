package org.zalava.web;

public interface WebPageRegistration {

  WebPageRegistration title(String title);

  WebPageRegistration description(String description);

  WebPageRegistration navSection(String navSection);

  WebPageRegistration get(String path, SeaWebHandler handler);

  WebPageRegistration post(String path, SeaWebHandler handler);
}
