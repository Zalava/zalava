package org.zalava.assistant.conversation.adapter.out.filesystem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.zalava.assistant.conversation.domain.ConversationMessage;

final class ConversationYamlSerializer {
  static List<ConversationMessage> deserialize(String body) {
    if (body == null || body.isBlank()) return List.of();
    List<Map<String, Object>> entries = new Yaml().load(body);
    if (entries == null) return List.of();
    return entries.stream()
        .map(
            entry -> {
              var item =
                  entry.entrySet().stream()
                      .filter(
                          value ->
                              !value.getKey().equals("attachments")
                                  && !value.getKey().equals("runId"))
                      .findFirst()
                      .orElseThrow();
              Object stored = entry.get("attachments");
              List<String> names =
                  stored instanceof List<?> values
                      ? values.stream().map(String.class::cast).toList()
                      : List.of();
              return new ConversationMessage(
                  ConversationMessage.Role.valueOf(item.getKey().toUpperCase(Locale.ROOT)),
                  (String) item.getValue(),
                  names,
                  (String) entry.get("runId"));
            })
        .toList();
  }

  static String serialize(List<ConversationMessage> messages) {
    List<Map<String, Object>> entries =
        messages.stream()
            .map(
                message -> {
                  Map<String, Object> entry = new LinkedHashMap<>();
                  entry.put(message.role().name().toLowerCase(Locale.ROOT), message.text());
                  if (!message.attachmentNames().isEmpty())
                    entry.put("attachments", message.attachmentNames());
                  if (message.runId() != null) entry.put("runId", message.runId());
                  return entry;
                })
            .toList();
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    return new Yaml(options).dump(entries);
  }
}
