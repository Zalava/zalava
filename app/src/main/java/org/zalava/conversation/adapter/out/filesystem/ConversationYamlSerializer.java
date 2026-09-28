package org.zalava.conversation.adapter.out.filesystem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.zalava.conversation.domain.ConversationMessage;

final class ConversationYamlSerializer {
  static List<ConversationMessage> deserialize(String body) {
    if (body == null || body.isBlank()) return List.of();
    List<Map<String, String>> entries = new Yaml().load(body);
    if (entries == null) return List.of();
    return entries.stream()
        .map(
            entry -> {
              var item = entry.entrySet().iterator().next();
              return new ConversationMessage(
                  ConversationMessage.Role.valueOf(item.getKey().toUpperCase()), item.getValue());
            })
        .toList();
  }

  static String serialize(List<ConversationMessage> messages) {
    List<Map<String, String>> entries =
        messages.stream()
            .map(
                message -> {
                  Map<String, String> entry = new LinkedHashMap<>();
                  entry.put(message.role().name().toLowerCase(), message.text());
                  return entry;
                })
            .toList();
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    return new Yaml(options).dump(entries);
  }
}
