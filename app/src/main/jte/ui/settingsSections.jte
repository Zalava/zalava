@import java.util.List
@param String current
<nav class="settings-sections" aria-label="Settings sections">
@for(String key : List.of("assistant", "provider", "modules", "channels", "permissions"))
<a href="/settings?section=${key}" aria-current="${current.equals(key) ? "page" : null}">${switch(key) { case "assistant" -> "Assistant"; case "provider" -> "Provider & workspace"; case "modules" -> "Modules"; case "channels" -> "Channel links"; default -> "Permissions & interface"; }}</a>
@endfor
<a href="/zalava/accounts" aria-current="${current.equals("accounts") ? "page" : null}">Manage accounts</a>
<a class="settings-subsection" href="/zalava/accounts/permissions" aria-current="${current.equals("account-permissions") ? "page" : null}">Permissions</a>
</nav>
