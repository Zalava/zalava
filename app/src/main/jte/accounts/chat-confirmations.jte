@import java.util.List
@import org.zalava.web.ui.ChatConfirmationController.Row
@import org.zalava.web.ui.ChatConfirmationController.AccountChoice
@import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice
@import org.zalava.web.ui.navigation.NavigationModel
@import org.springframework.security.web.csrf.CsrfToken
@param List<Row> tools
@param List<AccountChoice> people
@param String accountId = null
@param String message = null
@param String error = null
@param CsrfToken csrf
@param NavigationModel navigation
<!doctype html><html lang="en" data-theme="light"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Chat confirmation · Zalava accounts</title><link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css"><link rel="stylesheet" href="/zalava-tokens.css"><link rel="stylesheet" href="/zalava-ui.css"><script src="/zalava-ui.js" defer></script></head><body>
@template.ui.navigation(items = navigation.items(), activePage = "settings", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
<main class="section"><div class="container settings-layout">@template.ui.settingsSections(current = "accounts")<div class="settings-content accounts-layout">
<a href="/zalava/accounts">← Accounts</a><header class="mb-4"><h1 class="title is-3">Chat tool confirmation</h1>
<p>Choose whether each action requires confirmation in Chat. Individual settings override the current role. These settings do not grant tool access; other channels keep their own approval rules.</p>
<p>Existing policy preserves previously approved tool policies. Pending requests still require a decision.</p></header>
@if(message != null)<p class="notification is-success is-light" role="status">${message}</p>@endif
@if(error != null)<p class="notification is-danger is-light" role="alert">${error}</p>@endif
<form method="get" action="/zalava/accounts/chat-confirmations" class="mb-4"><label class="label" for="confirmation-account">Individual override</label><div class="select"><select name="accountId" id="confirmation-account" required><option value="">Choose an account</option>@for(var person : people)<option value="${person.id()}" selected="${person.id().equals(accountId)}">${person.login()} · ${person.role()}${person.enabled() ? "" : " · Disabled"}</option>@endfor</select></div> <button class="button is-link is-light" type="submit">Show individual settings</button></form>
@if(tools.isEmpty())<p class="notification is-light">No actions are available. Start a module to manage confirmation for its tools.</p>@else
<div class="tool-access-list">@for(var row : tools)<article class="box tool-access-row" data-confirmation-tool="${row.tool().key().providerId()}:${row.tool().key().toolName()}">
<div class="tool-access-copy"><h2 class="title is-5 mb-1">${row.tool().key().toolName()}</h2><p class="is-size-7 has-text-grey">${row.tool().providerName()} · ${row.tool().key().moduleId()}</p><p>${row.tool().description()}</p>@if(!row.tool().available())<span class="tag is-warning is-light">Provider unavailable</span>@endif</div>
<div class="tool-access-actions"><form method="post" action="/zalava/accounts/chat-confirmations/role" class="mb-3">
<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="moduleId" value="${row.tool().key().moduleId()}"><input type="hidden" name="providerId" value="${row.tool().key().providerId()}"><input type="hidden" name="toolName" value="${row.tool().key().toolName()}"><input type="hidden" name="role" value="ADMIN">
<label class="label is-small" for="admin-${row.tool().key().providerId()}-${row.tool().key().toolName()}">Administrator role</label><div class="select"><select name="choice" id="admin-${row.tool().key().providerId()}-${row.tool().key().toolName()}"><option value="INHERIT" selected="${row.adminDefault() == Choice.INHERIT}">Existing policy</option><option value="REQUIRE" selected="${row.adminDefault() == Choice.REQUIRE}">Require confirmation</option><option value="AUTO" selected="${row.adminDefault() == Choice.AUTO}" disabled="${!row.tool().available()}">Run without confirmation</option></select></div> <button class="button is-link" type="submit">Save administrator default</button></form>
<form method="post" action="/zalava/accounts/chat-confirmations/role" class="mb-3">
<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="moduleId" value="${row.tool().key().moduleId()}"><input type="hidden" name="providerId" value="${row.tool().key().providerId()}"><input type="hidden" name="toolName" value="${row.tool().key().toolName()}"><input type="hidden" name="role" value="MEMBER">
<label class="label is-small" for="member-${row.tool().key().providerId()}-${row.tool().key().toolName()}">Member role</label><div class="select"><select name="choice" id="member-${row.tool().key().providerId()}-${row.tool().key().toolName()}"><option value="INHERIT" selected="${row.memberDefault() == Choice.INHERIT}">Existing policy</option><option value="REQUIRE" selected="${row.memberDefault() == Choice.REQUIRE}">Require confirmation</option><option value="AUTO" selected="${row.memberDefault() == Choice.AUTO}" disabled="${!row.tool().available()}">Run without confirmation</option></select></div> <button class="button is-link" type="submit">Save member default</button></form>
@if(row.user() != null)<p class="mb-2">Effective: ${row.user().effective() == Choice.AUTO ? "Run without confirmation" : row.user().effective() == Choice.REQUIRE ? "Require confirmation" : "Existing approval policy"}${row.user().effective() == Choice.INHERIT ? "" : " · " + row.user().source()}</p>
<form method="post" action="/zalava/accounts/chat-confirmations/user"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="accountId" value="${accountId}"><input type="hidden" name="moduleId" value="${row.tool().key().moduleId()}"><input type="hidden" name="providerId" value="${row.tool().key().providerId()}"><input type="hidden" name="toolName" value="${row.tool().key().toolName()}">
<label class="label is-small" for="user-${row.tool().key().providerId()}-${row.tool().key().toolName()}">Individual override</label><div class="select"><select name="choice" id="user-${row.tool().key().providerId()}-${row.tool().key().toolName()}"><option value="INHERIT" selected="${row.user().override() == Choice.INHERIT}">Inherit role default</option><option value="REQUIRE" selected="${row.user().override() == Choice.REQUIRE}">Require confirmation</option><option value="AUTO" selected="${row.user().override() == Choice.AUTO}" disabled="${!row.tool().available()}">Run without confirmation</option></select></div> <button class="button is-link" type="submit">Save individual setting</button></form>@endif
</div></article>@endfor</div>@endif
</div></div></main></body></html>
