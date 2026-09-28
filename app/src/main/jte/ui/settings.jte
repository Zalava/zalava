@import org.zalava.ui.SettingsController.SettingsModel
@import org.springframework.security.web.csrf.CsrfToken

@param SettingsModel model
@param String settingsMessage
@param String settingsError
@param CsrfToken csrf
@param org.zalava.ui.navigation.NavigationModel navigation

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>SEA Settings</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
    <link rel="stylesheet" href="/sea-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "settings")

<main class="section">
    <div class="container">
        <header class="settings-heading mb-6">
            <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-2">Default workspace</p>
            <h1 class="title is-2 mb-3">Settings</h1>
            <p class="subtitle is-5 has-text-grey">
                Workspace instructions and the safe defaults currently applied by SEA.
            </p>
        </header>

        @if(settingsMessage != null)
            <article class="message is-success" role="status">
                <div class="message-body">${settingsMessage}</div>
            </article>
        @endif
        @if(settingsError != null)
            <article class="message is-danger" role="alert">
                <div class="message-body">${settingsError}</div>
            </article>
        @endif

        <div class="columns is-variable is-5">
            <div class="column is-two-thirds">
                <section class="box settings-card">
                    <p class="heading mb-1">Assistant</p>
                    <h2 class="title is-4 mb-2">Workspace instructions</h2>
                    <p class="has-text-grey mb-4">
                        These instructions guide assistant behavior for this workspace.
                    </p>

        <form method="post" action="/settings/instructions">
            <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                        <div class="field">
                            <label class="label" for="instructions">Instructions</label>
                            <div class="control">
                                <textarea id="instructions" class="textarea settings-editor"
                                          name="instructions" required>${model.instructions()}</textarea>
                            </div>
                        </div>
                        <div class="field mt-4">
                            <div class="control">
                                <button class="button is-primary" type="submit">Save instructions</button>
                            </div>
                        </div>
                    </form>
                </section>
            </div>
            <div class="column">
                <section class="box settings-card" aria-labelledby="module-configuration-heading">
                    <p class="heading mb-1">Modules</p>
                    <h2 id="module-configuration-heading" class="title is-5 mb-2">Configuration health</h2>
                    @if(model.moduleConfigurationHealth().isEmpty())
                        <p class="has-text-grey is-size-7">No loaded modules currently declare configuration.</p>
                    @else
                        @for(var module : model.moduleConfigurationHealth())
                            <p class="mb-3">
                                <a href="/modules/${module.moduleId()}/configuration" data-configuration-health-link="${module.moduleId()}">${module.moduleId()}</a><br>
                                <span class="tag is-light">${module.status()}</span>
                            </p>
                        @endfor
                    @endif
                </section>
            </div>

            <div class="column">
                <section class="box settings-card mb-5" aria-labelledby="telegram-heading">
                    <p class="heading mb-1">Channels</p>
                    <h2 id="telegram-heading" class="title is-5 mb-2">Telegram</h2>
                    @if(model.telegram().enabled())
                        <p class="has-text-grey is-size-7 mb-4">Enabled for &#64;${model.telegram().allowedUsername()}. A token is configured but never shown.</p>
                    @else
                        <p class="has-text-grey is-size-7 mb-4">Disabled. Enable it to receive messages through your Telegram bot.</p>
                    @endif
        <form method="post" action="/settings/channels/telegram">
            <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                        <div class="field">
                            <label class="checkbox" for="telegram-enabled">
                                <input id="telegram-enabled" type="checkbox" name="enabled" value="true" checked="${model.telegram().enabled()}">
                                Enable Telegram
                            </label>
                        </div>
                        <div class="field">
                            <label class="label" for="telegram-token">Bot token</label>
                            <div class="control">
                                <input id="telegram-token" class="input" type="password" name="tokenReplacement" placeholder="${model.telegram().tokenConfigured() ? "Leave blank to keep the current token" : "Paste the Telegram bot token"}" autocomplete="off">
                            </div>
                        </div>
                        <div class="field">
                            <label class="label" for="telegram-username">Allowed Telegram username</label>
                            <div class="control">
                                <input id="telegram-username" class="input" type="text" name="allowedUsername" value="${model.telegram().allowedUsername() == null ? "" : model.telegram().allowedUsername()}" placeholder="&#64;your_username" autocomplete="off" autocapitalize="none" spellcheck="false">
                            </div>
                            <p class="help">Only this username may use the bot. Enter it with or without &#64;.</p>
                        </div>
                        <button class="button is-primary" type="submit">Save Telegram settings</button>
                        <p class="help mt-2">Changes take effect after SEA restarts.</p>
                    </form>
                </section>

                <section class="box settings-card mb-5">
                    <p class="heading mb-1">Workspace</p>
                    <h2 class="title is-5 mb-4">Runtime configuration</h2>
                    <dl class="settings-details">
                        <div>
                            <dt>Location</dt>
                            <dd>${model.workspacePath()}</dd>
                        </div>
                        <div>
                            <dt>Chat provider</dt>
                            <dd>${model.providerLabel()}</dd>
                        </div>
                    </dl>
                    <a class="button is-light is-fullwidth mt-4" href="/onboarding/provider">
                        Open provider setup
                    </a>
                    <p class="help mt-2">Provider changes take effect after setup and restart.</p>
                </section>

                <section class="box settings-card">
                    <p class="heading mb-1">Safe defaults</p>
                    <h2 class="title is-5 mb-4">Permissions and interface</h2>
                    <div class="settings-default">
                        <div>
                            <p class="has-text-weight-semibold">Side-effecting tool calls</p>
                            <p class="has-text-grey is-size-7">Require your approval before execution.</p>
                        </div>
                        <span class="tag is-success is-light">Protected</span>
                    </div>
                    <div class="settings-default">
                        <div>
                            <p class="has-text-weight-semibold">Interface theme</p>
                            <p class="has-text-grey is-size-7">Pinned for consistent product readability.</p>
                        </div>
                        <span class="tag is-light">Light</span>
                    </div>
                </section>
            </div>
        </div>
    </div>
</main>

</body>
</html>
