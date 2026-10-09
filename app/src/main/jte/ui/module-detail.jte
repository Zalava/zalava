@import org.zalava.web.ui.navigation.NavigationModel
@import org.springframework.security.web.csrf.CsrfToken
@import org.zalava.web.ui.ModulesController.ModuleDetailModel
@import org.zalava.web.ui.ModulesController.ProviderEntry
@import org.zalava.web.ui.ModulesController.ManagedServiceEntry
@import org.zalava.web.ui.ModulesController.ScopeEntry
@import org.zalava.web.ui.ModulesController.ToolEntry
@import org.zalava.web.ui.ModulesController.CatalogReleaseEntry
@param ModuleDetailModel model
@param NavigationModel navigation
@param CsrfToken csrf

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>${model.displayName()} · Zalava Modules</title>
    <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
    <link rel="stylesheet" href="/zalava-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "modules")
<main class="section">
    <div class="container" data-module-detail="${model.moduleId()}">
        <a class="is-size-7" href="/modules">← Modules</a>
        <header class="mt-3 mb-5">
            <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-1">Module</p>
            <h1 class="title is-3 mb-2">${model.displayName()}</h1>
            <p class="has-text-grey">${model.moduleId()}</p>
            <p class="mt-3">
                <span class="tag ${model.statusClass()}" data-module-status>${model.statusLabel()}</span>
                <span class="tag is-light" data-runtime-state="${model.lifecycleState()}">${model.lifecycleState().replace('_', ' ')}</span>
                @if(model.version() != null)
                    <span class="tag is-light">Version ${model.version()}</span>
                @endif
                @if(model.enabledExternal())
                    <span class="tag is-info is-light">Enabled external</span>
                @endif
            </p>
        </header>
        @if(model.moduleMessage() != null)
            <div class="notification is-success" role="status">${model.moduleMessage()}</div>
        @endif
        @if(model.moduleError() != null)
            <div class="notification is-danger" role="alert">${model.moduleError()}</div>
        @endif
        @if(model.configurationMessage() != null)
            <div class="notification is-success" role="status">${model.configurationMessage()}</div>
        @endif
        @if(model.configurationError() != null)
            <div class="notification is-danger" role="alert">${model.configurationError()}</div>
        @endif
        @if(model.loaded() && model.enabledExternal())
            @if("RUNNING".equals(model.lifecycleState()))
                <form method="post" action="/modules/${model.moduleId()}/stop" class="mb-4">
                    <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                    <button class="button is-warning is-light" type="submit">Stop module</button>
                </form>
            @else
                <form method="post" action="/modules/${model.moduleId()}/start" class="mb-4">
                    <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                    <button class="button is-link" type="submit">Start module</button>
                </form>
            @endif
        @endif

        @if(model.updateAvailable())
            <article class="message is-warning" role="status" data-update-available>
                <div class="message-body">Update available: ${model.latestVersion()} (installed ${model.version()}).</div>
            </article>
        @endif

        @if(model.description() != null && !model.description().isBlank())
            <section class="box">
                <h2 class="title is-5">Description</h2>
                <p class="module-description">${model.description()}</p>
                <p class="has-text-grey is-size-7 mt-3">${model.provenance()}</p>
            </section>
        @endif

        <section class="box" aria-labelledby="module-configuration-heading">
            <div class="level is-mobile">
                <div class="level-left"><h2 id="module-configuration-heading" class="title is-5 mb-0">Configuration</h2></div>
                <div class="level-right">
            <span class="tag ${model.configuration().cssClass()}" data-configuration-status>${model.configuration().label()}</span>
            <span class="tag is-light" data-stored-secret-count>${model.storedSecretCount()} stored secret reference(s)</span>
                </div>
            </div>
        @if(!model.configurationFields().isEmpty())
            <form method="post" action="/modules/${model.moduleId()}/configuration" class="mt-5" id="configuration">
                <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                @for(var field : model.configurationFields())
                    <div class="field">
                        <label class="label" for="${field.name()}">${field.label()}</label>
                        @if(field.description() != null)
                            <p class="help mb-2">${field.description()}</p>
                        @endif
                        <div class="control">
                            @if(field.type().equals("json"))
                                <textarea id="${field.name()}" class="textarea is-family-monospace" name="${field.name()}" rows="5" required="${field.required()}">${field.value()}</textarea>
                            @else
                                @if(field.type().equals("boolean"))
                                    <label class="checkbox"><input id="${field.name()}" name="${field.name()}" type="checkbox" value="true" checked="${field.value().equals("true")}"> Enabled</label>
                                @else
                                    <input id="${field.name()}" class="input" name="${field.name()}" type="${field.type().equals("number") || field.type().equals("integer") ? "number" : "text"}" value="${field.value()}" required="${field.required()}">
                                @endif
                            @endif
                        </div>
                        @if(field.secretReference())
                            <div class="control mt-2">
                                <label class="label is-size-7" for="${field.replacementSecretName()}">Replace stored secret</label>
                                <input id="${field.replacementSecretName()}" class="input" name="${field.replacementSecretName()}" type="password" autocomplete="new-password" data-secret-replacement="${field.path()}">
                                <p class="help">Leave blank to keep stored value. Zalava never renders it.</p>
                            </div>
                        @endif
                    </div>
                @endfor
                <div class="field is-grouped mt-5">
                    <div class="control"><button class="button is-primary" type="submit">Save configuration</button></div>
                    @if(model.candidatePresent())
                        <div class="control"><button class="button is-light" type="submit" formaction="/modules/${model.moduleId()}/configuration/cancel">Discard pending changes</button></div>
                    @endif
                </div>
            </form>
        @else
            <p class="has-text-grey mt-4">No configuration is required.</p>
        @endif
        @if(model.enabledExternal())
                <form method="post" action="/modules/${model.moduleId()}/disable" class="mt-4">
                    <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                    <button class="button is-small is-danger is-light" type="submit" data-action="disable">Disable module</button>
                    <span class="has-text-grey is-size-7 ml-3">Restart Zalava to unload it from this running process.</span>
                </form>
            @endif
        </section>

        @if(!model.managedServices().isEmpty())
            <section class="box" aria-labelledby="module-managed-services-heading" data-managed-services>
                <h2 id="module-managed-services-heading" class="title is-5">Managed service setup</h2>
                <p class="has-text-grey is-size-7 mb-4">
                    Zalava installs and supervises these provider services. Complete each provider's own setup in its
                    interface, then save the module credential under Configuration.
                </p>
                @if(model.hasDeclaredManagedServices())
                    <form method="post" action="/modules/${model.moduleId()}/managed-services/request" class="mb-4">
                        <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                        <button class="button is-primary is-small" type="submit" data-action="install-managed-service">Install managed services</button>
                    </form>
                @endif
                @if(model.managedServiceRequestStatus() != null)
                    <p class="has-text-grey is-size-7 mb-2" data-managed-service-request-status>Install request status: ${model.managedServiceRequestStatus()}</p>
                @endif
                @if(model.managedServiceRequestStatus() != null && model.managedServiceRequestStatus().equals("PENDING"))
                    <div class="field is-grouped mb-4">
                        <form method="post" action="/modules/${model.moduleId()}/managed-services/approve" class="control">
                            <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                            <button class="button is-success is-small" type="submit" data-action="approve-managed-service">Approve installation</button>
                        </form>
                        <form method="post" action="/modules/${model.moduleId()}/managed-services/deny" class="control">
                            <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                            <button class="button is-light is-small" type="submit" data-action="deny-managed-service">Deny</button>
                        </form>
                    </div>
                @endif
                @if(model.managedServiceError() != null)
                    <article class="message is-danger" role="alert" data-managed-service-error>
                        <div class="message-body">${model.managedServiceError()}</div>
                    </article>
                @endif
                @for(ManagedServiceEntry service : model.managedServices())
                    <article class="module-managed-service mb-4" data-managed-service="${service.serviceId()}">
                        <p class="mb-1">
                            <strong>${service.serviceId()}</strong>
                            <span class="tag ${service.stateClass()}" data-managed-service-state>${service.stateLabel()}</span>
                            @if(service.consecutiveFailures() > 0)
                                <span class="tag is-danger is-light">${service.consecutiveFailures()} failed attempt(s)</span>
                            @endif
                        </p>
                        <p class="has-text-grey is-size-7">Image ${service.artifactReference()}</p>
                        @if(service.endpoints().isEmpty())
                            <p class="has-text-grey is-size-7 mt-2">No published endpoint yet.</p>
                        @else
                            @for(String endpoint : service.endpoints())
                                <p class="mt-2">
                                    <a class="button is-small is-link is-light" href="${endpoint}" target="_blank" rel="noopener" data-managed-service-endpoint>Open service interface — ${endpoint}</a>
                                </p>
                            @endfor
                        @endif
                    </article>
                @endfor
            </section>
        @endif

        <section class="box" aria-labelledby="module-permissions-heading">
            <h2 id="module-permissions-heading" class="title is-5">Declared permissions</h2>
            @if(model.declaredPermissions().isEmpty())
                <p class="has-text-grey">This module declares no permissions.</p>
            @else
                <ul data-declared-permissions>
                    @for(String permission : model.declaredPermissions())
                        <li><code>${permission}</code></li>
                    @endfor
                </ul>
            @endif
        </section>

        <section class="box" aria-labelledby="module-releases-heading">
            <h2 id="module-releases-heading" class="title is-5">Catalog releases</h2>
            @if(!model.catalogAvailable())
                <div class="notification is-light">Check the catalog on the Modules page to list releases for this module.</div>
            @else
                @if(model.releases().isEmpty())
                    <div class="notification is-light">The catalog lists no releases for this module.</div>
                @else
                    <form method="post" action="/modules/installation-requests" class="field is-grouped is-grouped-multiline">
                        <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                        <input type="hidden" name="moduleId" value="${model.moduleId()}">
                        <div class="control">
                            <label class="label" for="version">Release version</label>
                            <div class="select">
                                <select id="version" name="version" data-release-version>
                                    @for(CatalogReleaseEntry release : model.releases())
                                        @if(release.version().equals(model.latestVersion()))
                                            <option value="${release.version()}" selected>${release.version()}</option>
                                        @else
                                            <option value="${release.version()}">${release.version()}</option>
                                        @endif
                                    @endfor
                                </select>
                            </div>
                        </div>
                        <div class="control is-align-self-flex-end">
                            <button class="button is-primary" type="submit" data-action="request-installation">
                                @if(model.version() != null)
                                    Install update
                                @else
                                    Install module
                                @endif
                            </button>
                        </div>
                    </form>
                @endif
            @endif
        </section>

        <section class="box" aria-labelledby="module-providers-heading">
            <h2 id="module-providers-heading" class="title is-5">Providers and tools</h2>
            @if(model.providers().isEmpty())
                <p class="has-text-grey">No providers are loaded for this module.</p>
            @else
                @for(ProviderEntry provider : model.providers())
                    <article class="module-provider mb-4" data-provider="${provider.providerId()}">
                        <h3 class="title is-6 mb-1">${provider.displayName()}</h3>
                        <p class="has-text-grey is-size-7 mb-2">${provider.providerId()} · ${provider.providerType()}</p>
                        <p class="module-description">${provider.description()}</p>
                        @if(!provider.scope().isEmpty())
                            <p class="mt-3 mb-1"><strong>Scope</strong></p>
                            <ul>
                                @for(ScopeEntry scope : provider.scope())
                                    <li class="is-size-7">${scope.name()} = ${scope.value()}</li>
                                @endfor
                            </ul>
                        @endif
                        <p class="mt-3 mb-1"><strong>Tools</strong></p>
                        @if(provider.tools().isEmpty())
                            <p class="has-text-grey is-size-7">No tools.</p>
                        @else
                            <ul>
                                @for(ToolEntry tool : provider.tools())
                                    <li class="is-size-7">
                                        <code>${tool.name()}</code>
                                        @if(tool.sideEffecting())
                                            <span class="tag is-warning is-light">side effecting</span>
                                        @endif
                                        — ${tool.description()}
                                    </li>
                                @endfor
                            </ul>
                        @endif
                    </article>
                @endfor
            @endif
        </section>
    </div>
</main>
</body>
</html>
