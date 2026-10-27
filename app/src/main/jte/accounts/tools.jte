@import java.util.List
@import org.zalava.web.ui.MemberToolAccessController.ToolRow
@import org.zalava.web.ui.navigation.NavigationModel
@import org.springframework.security.web.csrf.CsrfToken
@param String member
@param String accountId
@param List<ToolRow> tools
@param String message = null
@param String error = null
@param CsrfToken csrf
@param NavigationModel navigation
<!doctype html>
<html lang="en" data-theme="light"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Tool access · Zalava accounts</title>
<link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css"><link rel="stylesheet" href="/zalava-tokens.css"><link rel="stylesheet" href="/zalava-ui.css"><script src="/zalava-ui.js" defer></script>
</head><body>
@template.ui.navigation(items = navigation.items(), activePage = "settings", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
<main class="section"><div class="container settings-layout">
@template.ui.settingsSections(current = "accounts")
<div class="settings-content accounts-layout">
<a href="/zalava/accounts">← Accounts</a>
<header class="mb-4"><h1 class="title is-3">Tool access for ${member}</h1><p>Choose user overrides or inherit the <a href="/zalava/accounts/tool-defaults">role defaults</a>. Scope restrictions and approvals still apply.</p></header>
@if(message != null)<p class="notification is-success is-light" role="status">${message}</p>@endif
@if(error != null)<p class="notification is-danger is-light" role="alert">${error}</p>@endif
@if(tools.isEmpty())
<p class="notification is-light">No tools are available. Start a module to manage its tools here.</p>
@else
<div class="tool-access-list">
@for(var tool : tools)
<article class="box tool-access-row" data-tool-access="${tool.key().providerId()}:${tool.key().toolName()}">
<div class="tool-access-copy"><h2 class="title is-5 mb-1">${tool.key().toolName()}</h2><p class="is-size-7 has-text-grey">${tool.providerName()} · ${tool.key().moduleId()}</p><p>${tool.description()}</p>
@if(tool.changedAt() != null)<p class="is-size-7 has-text-grey">Changed <time datetime="${tool.changedAt()}" data-local-time>${tool.changedAt()}</time></p>@endif
</div>
<div class="tool-access-actions"><p>${tool.source()} · ${tool.membersAllowed() ? "All Members" : "Administrators only"}</p><span class="tag ${tool.allowed() ? "is-success is-light" : "is-light"}">${tool.allowed() ? "Allowed" : "Not allowed"}</span>
@if(!tool.available())<span class="tag is-warning is-light">Provider unavailable</span>@endif
@if(tool.allowed() || tool.eligible() || !tool.override().equals("INHERIT") || !tool.available())
<form method="post" action="/zalava/accounts/${accountId}/tools">
<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="moduleId" value="${tool.key().moduleId()}"><input type="hidden" name="providerId" value="${tool.key().providerId()}"><input type="hidden" name="toolName" value="${tool.key().toolName()}"><label class="label is-small" for="decision-${tool.key().providerId()}-${tool.key().toolName()}">User override</label>
<div class="select"><select name="decision" id="decision-${tool.key().providerId()}-${tool.key().toolName()}"><option value="INHERIT" selected="${tool.override().equals("INHERIT")}">Inherit role default</option><option value="ALLOW" selected="${tool.override().equals("ALLOW")}" disabled="${!tool.eligible()}">Allow</option><option value="DENY" selected="${tool.override().equals("DENY")}">Deny</option></select></div>
<button class="button is-link" type="submit">Save override</button></form>
@else
<p class="help">${tool.available() ? "Restricted by host policy" : "Start this provider before granting access"}</p>
@endif
</div></article>
@endfor
</div>
@endif
</div></div></main></body></html>
