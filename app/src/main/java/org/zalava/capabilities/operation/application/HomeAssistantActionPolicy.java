package org.zalava.capabilities.operation.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountId;

/** Host-owned boundary for dynamic Home Assistant side effects. */
final class HomeAssistantActionPolicy {
  private static final Map<String, Set<String>> ACTIONS =
      Map.of(
          "light", Set.of("turn_on", "turn_off", "toggle"),
          "switch", Set.of("turn_on", "turn_off", "toggle"),
          "fan", Set.of("turn_on", "turn_off", "toggle", "set_percentage"),
          "climate", Set.of("turn_on", "turn_off", "set_temperature", "set_hvac_mode"),
          "cover", Set.of("open_cover", "close_cover", "stop_cover", "set_cover_position"),
          "media_player",
              Set.of(
                  "turn_on",
                  "turn_off",
                  "toggle",
                  "volume_set",
                  "volume_up",
                  "volume_down",
                  "media_play",
                  "media_pause",
                  "media_stop"));
  private static final Set<String> TARGET_KEYS =
      Set.of("entity_id", "area_id", "device_id", "floor_id", "label_id", "target");
  private final AccountLifecycle accounts;

  HomeAssistantActionPolicy(AccountLifecycle accounts) {
    this.accounts = accounts;
  }

  static boolean applies(ZalavaProvider provider, ZalavaToolDescriptor tool) {
    return "zalava-module-home-assistant".equals(provider.descriptor().moduleId())
        && "home_assistant_execute_action".equals(tool.name());
  }

  void require(InvocationContext context, Map<String, Object> args) {
    if (context.attributes().getOrDefault("permissionPolicyChannel", "").isBlank()
        || context.attributes().getOrDefault("conversationId", "").isBlank()) deny();
    try {
      var account =
          accounts.findById(new AccountId(UUID.fromString(context.actorId()))).orElseThrow();
      if (!account.enabled()
          || account.passwordChangeRequired()
          || !account.role().name().equals(context.attributes().get("accountRole"))) deny();
    } catch (RuntimeException invalid) {
      deny();
    }
    if (!Set.of("domain", "action", "entityId", "serviceData", "returnResponse")
        .containsAll(args.keySet())) deny();
    Object domain = args.get("domain"), action = args.get("action"), target = args.get("entityId");
    if (!(domain instanceof String d)
        || !ACTIONS.containsKey(d)
        || !(action instanceof String a)
        || !ACTIONS.get(d).contains(a)
        || !(target instanceof String e)
        || !e.matches("[a-z0-9_]{1,128}\\.[a-z0-9_]{1,128}")
        || !e.startsWith(d + ".")) deny();
    Object data = args.getOrDefault("serviceData", Map.of());
    if (!(data instanceof Map<?, ?> map)
        || map.entrySet().stream()
            .anyMatch(
                entry ->
                    !(entry.getKey() instanceof String)
                        || TARGET_KEYS.contains(entry.getKey())
                        || !(entry.getValue() instanceof String
                            || entry.getValue() instanceof Number
                            || entry.getValue() instanceof Boolean))) deny();
    if (args.containsKey("returnResponse") && !(args.get("returnResponse") instanceof Boolean))
      deny();
  }

  private static void deny() {
    throw new ProviderToolOperationException(
        ProviderToolOperationException.Code.UNSUPPORTED,
        "Home Assistant action denied by host policy");
  }
}
