package org.zalava.api.extensions.web;

public record ZalavaWebResponse(int status, String contentType, String body) {

  public static ZalavaWebResponse html(String body) {
    return new ZalavaWebResponse(200, "text/html", body);
  }

  public static ZalavaWebResponse html(int status, String body) {
    return new ZalavaWebResponse(status, "text/html", body);
  }
}
