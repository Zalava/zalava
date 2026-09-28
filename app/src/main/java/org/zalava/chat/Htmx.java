package org.zalava.chat;

/** Generic htmx out-of-band (OOB) swap HTML fragment helpers. */
public class Htmx {

  private Htmx() {}

  public static String oobAppend(String id, String content) {
    return "<div id=\"" + id + "\" hx-swap-oob=\"beforeend\">" + content + "</div>";
  }

  public static String oobReplace(String id, String content) {
    return "<div id=\"" + id + "\" hx-swap-oob=\"true\">" + content + "</div>";
  }

  public static String oobInnerHtml(String id, String content) {
    return "<div id=\"" + id + "\" hx-swap-oob=\"innerHTML\">" + content + "</div>";
  }

  /** Removes the element with the given id on the next htmx message swap. */
  public static String oobDelete(String id) {
    return "<div id=\"" + id + "\" hx-swap-oob=\"delete\"></div>";
  }
}
