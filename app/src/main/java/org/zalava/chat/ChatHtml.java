package org.zalava.chat;

import java.util.List;
import java.util.UUID;
import org.springframework.web.util.HtmlUtils;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskReference;

/** Chat message bubble HTML fragment helpers. */
public class ChatHtml {

  private ChatHtml() {}

  public static String agentBubble(String text) {
    return agentBubble(text, List.of());
  }

  public static String agentBubble(String text, List<TaskReference> jobReferences) {
    return """
                <article class="ar-msg ar-msg--agent">\
                <div class="ar-msg__avatar">S</div>\
                <div class="ar-msg__bubble"><div>%s</div>%s</div>\
                </article>"""
        .formatted(HtmlUtils.htmlEscape(text), jobLinks(jobReferences));
  }

  public static String userBubble(String text) {
    return """
                <article class="ar-msg ar-msg--user">\
                <div class="ar-msg__bubble">%s</div>\
                </article>"""
        .formatted(HtmlUtils.htmlEscape(text));
  }

  public static String actorAgentBubble(String text, List<ActorTaskReference> jobReferences) {
    return """
                <article class="ar-msg ar-msg--agent">\
                <div class="ar-msg__avatar">S</div>\
                <div class="ar-msg__bubble"><div>%s</div>%s</div>\
                </article>"""
        .formatted(HtmlUtils.htmlEscape(text), actorJobLinks(jobReferences));
  }

  public static String typingDots() {
    return """
                <div class="ar-typing">\
                <div class="ar-msg__avatar">S</div>\
                <div class="ar-typing__dots"><span></span><span></span><span></span></div>\
                </div>""";
  }

  /**
   * Streaming agent bubble: same look as a regular agent bubble, with an inner element id used by
   * the WebSocket handler to replace the accumulated text as deltas arrive.
   */
  public static String streamingAgentBubble(String text) {
    return """
                <article class="ar-msg ar-msg--agent">\
                <div class="ar-msg__avatar">S</div>\
                <div class="ar-msg__bubble"><div id="streaming-bubble">%s</div></div>\
                </article>"""
        .formatted(HtmlUtils.htmlEscape(text));
  }

  public static String chatInputArea(String conversationId) {
    if (isWebConversation(conversationId)) {
      return """
                    <form id="chat-form" ws-send hx-boost="false"
                          hx-vals='js:{"type": "userMessage", "conversationId": document.getElementById("channel-select") ? document.getElementById("channel-select").value : "web"}'>
                        <div class="field is-grouped chat-input-group">
                            <div class="control is-expanded">
                                <label class="is-sr-only" for="message-input">Message SEA</label>
                                <textarea id="message-input" class="textarea" name="message" rows="1"
                                    placeholder="Message SEA..."
                                    autocomplete="off" spellcheck="true" autofocus></textarea>
                            </div>
                            <div class="control">
                                <button id="send-btn" type="submit" class="button is-primary" title="Send (Enter)"
                                        aria-label="Send message">
                                    <span class="icon">
                                        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true">
                                            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                                        </svg>
                                    </span>
                                </button>
                            </div>
                        </div>
                    </form>""";
    }
    String label = HtmlUtils.htmlEscape(labelFor(conversationId));
    return """
                <p class="chat-readonly-notice">This is a read-only view of <strong>%s</strong>. \
                Open %s to continue the conversation.</p>"""
        .formatted(label, label);
  }

  public static String conversationSelector(List<String> ids, String selectedId) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        """
                <div class="chat-conversation-controls">
                <select id="channel-select" class="select" name="conversationId" \
                ws-send hx-trigger="change" \
                hx-vals='{"type": "channelChanged"}'>""");
    for (String id : ids) {
      sb.append("<option value=\"").append(HtmlUtils.htmlEscape(id)).append("\"");
      if (id.equals(selectedId)) sb.append(" selected");
      sb.append(">").append(HtmlUtils.htmlEscape(labelFor(id))).append("</option>");
    }
    sb.append(
        """
                </select>\
                <form id="new-conversation-form" ws-send hx-boost="false" \
                hx-vals='{"type": "createConversation"}'>\
                <button type="submit" class="button is-small is-primary is-light" \
                title="New conversation" aria-label="New conversation">New</button>\
                </form>\
                </div>""");
    return sb.toString();
  }

  private static String labelFor(String conversationId) {
    if ("web".equals(conversationId)) return "Web Chat";
    if (conversationId.startsWith("web-"))
      return "Web Chat " + conversationId.substring("web-".length());
    if (isActorWebConversation(conversationId)) return "Web Chat " + conversationId.substring(0, 8);
    if (conversationId.startsWith("telegram-"))
      return "Telegram (" + conversationId.substring("telegram-".length()) + ")";
    return conversationId;
  }

  private static boolean isWebConversation(String conversationId) {
    return "web".equals(conversationId)
        || (conversationId != null && conversationId.startsWith("web-"))
        || isActorWebConversation(conversationId);
  }

  /**
   * Actor web conversations use opaque bare-UUID references; non-web channels such as {@code
   * telegram-*} are the only conversations that stay read-only.
   */
  private static boolean isActorWebConversation(String conversationId) {
    if (conversationId == null || conversationId.startsWith("telegram-")) return false;
    try {
      UUID.fromString(conversationId);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private static String jobLinks(List<TaskReference> jobReferences) {
    if (jobReferences.isEmpty()) {
      return "";
    }
    StringBuilder links = new StringBuilder("<div class=\"ar-msg__jobs\">");
    for (TaskReference reference : jobReferences) {
      links
          .append("<a class=\"button is-small is-primary is-light\" href=\"/jobs/")
          .append(HtmlUtils.htmlEscape(reference.path()))
          .append("\">View job</a>");
    }
    return links.append("</div>").toString();
  }

  private static String actorJobLinks(List<ActorTaskReference> jobReferences) {
    if (jobReferences.isEmpty()) return "";
    StringBuilder links = new StringBuilder("<div class=\"ar-msg__jobs\">");
    for (ActorTaskReference reference : jobReferences) {
      links
          .append("<a class=\"button is-small is-primary is-light\" href=\"/jobs/")
          .append(HtmlUtils.htmlEscape(reference.value()))
          .append("\">View job</a>");
    }
    return links.append("</div>").toString();
  }
}
