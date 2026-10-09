@import java.time.Instant
@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.web.ui.SettingsController.SettingsModel
@import org.springframework.security.web.csrf.CsrfToken
@import java.util.List
@param SettingsModel model
@param String section
@param String settingsMessage
@param String settingsError
@param String channelLinkCode
@param Instant channelLinkExpiresAt
@param CsrfToken csrf
@param NavigationModel navigation
<!doctype html><html lang="en" data-theme="light"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Zalava Settings</title>
<link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css"><link rel="stylesheet" href="/zalava-ui.css">
</head><body>
@template.ui.navigation(items = navigation.items(), activePage = "settings")
<main class="section"><div class="container">
<header class="settings-heading mb-5"><p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-2">Default workspace</p>
<h1 class="title is-2 mb-3">Settings</h1><p class="subtitle is-5 has-text-grey">Shape your assistant and configure your workspace.</p></header>
@if(settingsMessage != null)<article class="message is-success" role="status"><div class="message-body">${settingsMessage}</div></article>@endif
@if(settingsError != null)<article class="message is-danger" role="alert"><div class="message-body">${settingsError}</div></article>@endif
<div class="settings-layout">
<nav class="settings-sections" aria-label="Settings sections">
@for(String key : List.of("assistant", "provider", "modules", "channels", "permissions"))
<a href="/settings?section=${key}" aria-current="${section.equals(key) ? "page" : null}">${switch(key) { case "assistant" -> "Assistant"; case "provider" -> "Provider & workspace"; case "modules" -> "Modules"; case "channels" -> "Channel links"; default -> "Permissions & interface"; }}</a>
@endfor
</nav><div class="settings-content">
@if(section.equals("assistant"))
<section class="box settings-card" aria-labelledby="instructions-heading">
<div class="settings-actions mb-3"><h2 id="instructions-heading" class="title is-4 mb-0">Workspace instructions</h2><span class="tag is-light">${model.instructionsCustomized() ? "Customized" : "Default"}</span></div>
<p class="has-text-grey mb-4">Guide how Zalava communicates, uses tools and carries out work. Changes apply to subsequent requests. System permissions and approvals always apply.</p>
<form method="post" action="/settings/instructions" id="instructions-form"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
<div class="field"><label class="label" for="instructions">Instructions</label><textarea id="instructions" class="textarea settings-editor" name="instructions" required>${model.instructions()}</textarea></div></form>
<div class="settings-actions mt-4"><button class="button is-primary" type="submit" form="instructions-form">Save instructions</button>
<form method="post" action="/settings/instructions/reset"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><button class="button is-light" type="submit">Reset to defaults</button></form></div>
<p class="help mt-3">Reset replaces your saved instructions with Zalava's recommended defaults.</p></section>
@elseif(section.equals("provider"))
<section class="box settings-card"><h2 class="title is-4">Provider & workspace</h2><dl class="settings-details"><div><dt>Location</dt><dd>${model.workspacePath()}</dd></div><div><dt>Chat provider</dt><dd>${model.providerLabel()}</dd></div></dl>
<a class="button is-primary mt-4" href="/onboarding/provider">Open provider setup</a><p class="help mt-2">Provider changes take effect after setup and restart.</p></section>
@elseif(section.equals("modules"))
<section class="box settings-card" aria-labelledby="module-configuration-heading"><h2 id="module-configuration-heading" class="title is-4">Module configuration</h2><p class="has-text-grey mb-4">Each installed module owns its settings, including channel credentials.</p>
@if(model.moduleConfigurationHealth().isEmpty())<p>No loaded modules currently declare configuration.</p><a class="button is-light mt-4" href="/modules">Browse modules</a>
@else @for(var module : model.moduleConfigurationHealth())<div class="settings-default"><a href="/modules/${module.moduleId()}/configuration" data-configuration-health-link="${module.moduleId()}">${module.moduleId()}</a><span class="tag is-light">${module.status()}</span></div>@endfor @endif
</section>
@elseif(section.equals("channels"))
<section class="box settings-card" aria-labelledby="channel-links-heading"><h2 id="channel-links-heading" class="title is-4">Channel links</h2><p class="has-text-grey mb-4">Create a one-time code for your account, then send it through the channel you want to link. A username is never used as authority. Configure the channel in its module settings.</p>
@if(channelLinkCode != null)<article class="message is-success" role="status"><div class="message-body">Link code: <code>${channelLinkCode}</code><br>Expires: ${channelLinkExpiresAt.toString()}</div></article>@endif
@if(model.availableChannels().isEmpty())<p>No active channel modules are available. Install and configure a channel module first.</p><a class="button is-light mt-4" href="/modules">Open modules</a>
@else<form method="post" action="/settings/channel-links"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><div class="field"><label class="label" for="channel-link-channel">Channel</label><div class="select"><select id="channel-link-channel" name="channel" required>@for(String channel : model.availableChannels())<option value="${channel}">${channel}</option>@endfor</select></div></div><button class="button is-primary" type="submit">Create link code</button></form>@endif
</section>
@else
<section class="box settings-card"><h2 class="title is-4">Permissions & interface</h2><div class="settings-default"><div><p class="has-text-weight-semibold">Side-effecting tool calls</p><p class="has-text-grey">Require your approval before execution.</p></div><span class="tag is-success is-light">Protected</span></div><div class="settings-default"><div><p class="has-text-weight-semibold">Interface theme</p><p class="has-text-grey">Pinned for consistent product readability.</p></div><span class="tag is-light">Light</span></div><a class="button is-light mt-4" href="/zalava/control">Open advanced settings</a></section>
@endif
</div></div></div></main></body></html>
