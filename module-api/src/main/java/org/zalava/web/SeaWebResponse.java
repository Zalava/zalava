package org.zalava.web;

public record SeaWebResponse(int status, String contentType, String body) {

  public static SeaWebResponse html(String body) {
    return new SeaWebResponse(200, "text/html", body);
  }

  public static SeaWebResponse html(int status, String body) {
    return new SeaWebResponse(status, "text/html", body);
  }
}
