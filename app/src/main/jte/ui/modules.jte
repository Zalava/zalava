@import org.zalava.web.ui.navigation.NavigationModel
@import org.springframework.security.web.csrf.CsrfToken
@import org.zalava.web.ui.ModulesController.ModulesModel
@import org.zalava.web.ui.ModulesController.ModuleEntry
@import org.zalava.web.ui.ModulesController.EnabledModuleEntry
@import org.zalava.web.ui.ModulesController.CatalogModuleEntry
@import org.zalava.web.ui.ModulesController.InstallationRequestEntry
@param ModulesModel model
@param String marketplaceMessage
@param String catalogQuery
@param String marketplaceError
@param NavigationModel navigation
@param CsrfToken csrf

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Modules</title>
    <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
    <link rel="stylesheet" href="/zalava-tokens.css">
<link rel="stylesheet" href="/zalava-ui.css">
<script src="/zalava-ui.js" defer></script>
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "modules", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
<main class="section">
    <div class="container">
        <header class="modules-heading mb-5">
            <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-1">Default workspace</p>
            <h1 class="title is-3 mb-2">Modules</h1>
            <p class="has-text-grey">Installed capabilities and the catalog Zalava can install from.</p>
        </header>

        @if(marketplaceMessage != null)
            <article class="message is-success modules-message" role="status"><div class="message-body">${marketplaceMessage}</div></article>
        @endif
    @if(marketplaceError != null)
      <article class="message is-danger modules-message" role="alert"><div class="message-body">${marketplaceError}</div></article>
    @endif






  <section class="box module-list" aria-labelledby="managed-modules-heading">
<h2 id="managed-modules-heading" class="title is-5 mb-3">Installed modules</h2>
@if(model.modules().isEmpty())<p class="notification is-light">No modules are installed or loaded.</p>@else
<div class="module-entries">
@for(var module : model.modules())
<article class="module-entry managed-module-row" data-module="${module.moduleId()}" data-enabled-module="${module.installed() ? module.moduleId() : null}"><div class="module-copy">
<div class="module-heading item-heading"><h3 class="title is-6 mb-1"><a href="/modules/${module.moduleId()}" data-module-detail-link="${module.moduleId()}">${module.displayName()}</a></h3><span class="tag is-light">${module.state()}</span>@if(module.loaded())<span class="tag is-light">Loaded</span>@endif</div>
<p class="is-size-7 has-text-grey">Version ${module.version()}</p>@if(module.loaded() && module.description() != null && !module.description().isBlank() && !module.description().equalsIgnoreCase(module.displayName()))<p>${module.description()}</p>@endif<details class="module-source-details"><summary>Source and permissions</summary><p class="is-size-7 has-text-grey">@if(module.source() != null && !module.source().isBlank())${module.source()} · @endif${module.permissionCount()} ${module.permissionCount() == 1 ? "permission" : "permissions"}</p></details></div>
@if(module.loaded())<div class="module-meta"><span class="tag is-light">${module.providerCount()} provider(s)</span></div>@endif</article>
@endfor
</div>@endif
@if(model.moduleRestartRequired() || !model.restart().phase().equals("IDLE"))
<div class="module-restart-notice mt-4" data-zalava-restart-status="${model.restart().phase()}" role="status">
@if(model.moduleRestartRequired())<p>Restart Zalava to apply module changes.</p>@endif
@if(!model.restart().phase().equals("IDLE"))<p class="has-text-grey is-size-7">${model.restart().message()}</p>@endif
@if(model.moduleRestartRequired() || model.restart().requested() || model.restart().phase().equals("FAILED"))
<p class="has-text-grey is-size-7">Restarting briefly disconnects everyone. Sign in again when Zalava is ready.</p>
@if(model.restart().available())
<form method="post" action="/modules/restart" class="mt-2"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><button class="button is-warning is-light is-small" type="submit" data-action="restart-zalava" disabled="${model.restart().requested()}">Restart Zalava</button></form>
@else
<p class="has-text-grey is-size-7">Use your local service manager to restart Zalava.</p>
@endif
@endif
</div>
@endif
</section>

<section class="box module-list mt-5" aria-labelledby="marketplace-heading">
            <div class="level is-mobile mb-4">
                <div class="level-left">
                    <div>
                        <h2 id="marketplace-heading" class="title is-5 mb-1">Find capabilities</h2>
                        <p class="has-text-grey is-size-7">Catalog modules available to install or update.</p>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-light" data-metric="catalog-modules">${model.catalogModules().size()}</span>
                </div>
            </div>

            <form method="post" action="/modules/catalog/refresh" class="mb-4">
                <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                <button class="button is-light" type="submit" data-action="refresh-catalog">Check catalog</button>
                @if(model.catalogRefreshedAt() != null)
                    <p class="has-text-grey is-size-7 mt-2" data-catalog-refreshed-at>Last checked <time datetime="${model.catalogRefreshedAt()}" data-local-time>${model.catalogRefreshedAt()}</time></p>
                @endif
            </form>

            <form method="get" action="/modules" class="module-capability-search mb-4" role="search">
                <div class="field"><label class="label" for="module-search">Search capabilities</label>
                    <div class="module-search-input">
                        <input class="input" id="module-search" name="q" value="${catalogQuery}" maxlength="200" placeholder="Search by capability or module name">
                        @if(!catalogQuery.isBlank())
                            <a class="module-search-clear" href="/modules" aria-label="Clear search" title="Clear search">×</a>
                        @endif
                    </div>
                </div>
                <button class="button is-link" type="submit">Search</button>
            </form>
            @if(model.catalogModules().isEmpty())
                @if(!catalogQuery.isBlank() && model.catalogRefreshedAt() != null)
                    <div class="notification is-light" data-marketplace-empty>No modules match this search. Try another capability or clear the search.</div>
                @else
                    <div class="notification is-light" data-marketplace-empty>No catalog is loaded. Check the catalog to list available modules.</div>
                @endif
            @else
                <div class="module-entries">
                    @for(CatalogModuleEntry module : model.catalogModules())
                        <article class="module-entry" data-catalog-module="${module.moduleId()}">
                            <div class="module-copy">
                                <div class="module-heading item-heading">
                                    <h3 class="title is-6 mb-1"><a href="/modules/${module.moduleId()}" data-catalog-detail-link="${module.moduleId()}">${module.displayName()}</a></h3>
                                    @if(module.installed())
                                        <span class="tag is-info is-light" data-catalog-installed>Installed ${module.installedVersion()}</span>
                                    @else
                                        <span class="tag is-light">Not installed</span>
                                    @endif
                                </div>
                                <p class="module-description">${module.description()}</p>
                            </div>
                        </article>
                    @endfor
                </div>
            @endif
        </section>
<section class="box module-list mt-5" aria-labelledby="installation-requests-heading">
            <div class="level is-mobile mb-4">
                <div class="level-left">
                    <div>
                        <h2 id="installation-requests-heading" class="title is-5 mb-1">Awaiting approval</h2>
                        <p class="has-text-grey is-size-7">Review module installations before approval.</p>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-light" data-metric="installation-requests">${model.pendingInstallations().size()}</span>
                </div>
            </div>

            @if(model.pendingInstallations().isEmpty())
                <div class="notification is-light">No installations awaiting approval. <a href="/monitoring#module-installations">View installation history in Monitoring</a>.</div>
            @else
                <div class="module-entries">
                    @for(InstallationRequestEntry request : model.pendingInstallations())
                        <article class="module-entry" data-installation-request="${request.requestId()}">
                            <div class="module-copy">
                                <div class="module-heading">
                                    <h3 class="title is-6 mb-1">${request.moduleId()} ${request.version()}</h3>
                                    <span class="tag ${request.statusClass()}" data-request-status>${request.status()}</span>
                                </div>
                                <p class="is-size-7">Requested <time datetime="${request.createdAt()}" data-local-time>${request.createdAt()}</time></p>
<p class="has-text-grey is-size-7 mb-1" data-artifact-digest>${request.artifactDigest()}</p>
                                <p class="has-text-grey is-size-7 mb-2">Repository ${request.repositoryId()}</p>
                                @if(request.message() != null && !request.message().isBlank())
                                    <p class="module-description" data-request-message>${request.message()}</p>
                                @endif
                                @if(request.pending())
                                    <div class="field is-grouped mt-3">
                                        <div class="control">
                                            <form method="post" action="/modules/installation-requests/${request.requestId()}/allow">
                                                <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                                                <button class="button is-primary is-small" type="submit" data-action="allow">Allow</button>
                                            </form>
                                        </div>
                                        <div class="control">
                                            <form method="post" action="/modules/installation-requests/${request.requestId()}/deny">
                                                <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                                                <button class="button is-light is-small" type="submit" data-action="deny">Deny</button>
                                            </form>
                                        </div>
                                    </div>
                                @endif
                            </div>
                        </article>
                    @endfor
                </div>
            @endif
        </section>
        <details class="box module-list mt-5" data-module-developer-options>
            <summary class="has-text-weight-semibold">Developer options</summary>
            <p class="has-text-grey mt-3 mb-4">Install a custom module artifact or an accepted local development build.</p>
<section class="box module-list mb-5" aria-labelledby="uploaded-module-installation-heading">
    <h2 id="uploaded-module-installation-heading" class="title is-5 mb-1">Upload a module JAR</h2>
    <p class="has-text-grey is-size-7 mb-4">Zalava reads the module metadata and checksum from the JAR, then asks for installation approval. Uploading never starts the module.</p>
    <form method="post" action="/modules/upload-installations" enctype="multipart/form-data" class="columns is-multiline">
      <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
      <div class="column is-9"><label class="label is-small" for="module-jar">Module JAR</label><input class="input" id="module-jar" name="moduleJar" type="file" accept=".jar,application/java-archive" required></div>
      <div class="column is-3 is-flex is-align-items-flex-end"><button class="button is-link is-fullwidth" type="submit" data-action="upload-module">Upload for approval</button></div>
    </form>
  </section>
<section class="box module-list mb-5" aria-labelledby="local-module-installation-heading">
    <h2 id="local-module-installation-heading" class="title is-5 mb-1">Install a local build</h2>
    <p class="has-text-grey is-size-7 mb-4">Prepare an approval request from an accepted local development candidate. The project must be under Zalava's mounted module workspace and already built.</p>
    <form method="post" action="/modules/local-project-installations" class="columns is-multiline">
      <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
      <div class="column is-5"><label class="label is-small" for="local-project-directory">Mounted project directory</label><input class="input" id="local-project-directory" name="projectDirectory" required maxlength="2000" placeholder="/var/lib/zalava/modules/example"></div>
      <div class="column is-2"><label class="label is-small" for="local-module-id">Module id</label><input class="input" id="local-module-id" name="moduleId" required maxlength="160" placeholder="zalava-module-example"></div>
      <div class="column is-2"><label class="label is-small" for="local-module-version">Version</label><input class="input" id="local-module-version" name="version" required maxlength="160" placeholder="1.0.0"></div>
      <div class="column is-2"><label class="label is-small" for="local-development-request">Accepted development request</label><input class="input" id="local-development-request" name="developmentRequestId" required maxlength="128"></div>
      <div class="column is-1 is-flex is-align-items-flex-end"><button class="button is-link is-fullwidth" type="submit" data-action="prepare-local-module">Prepare</button></div>
    </form>
  </section>
        </details>
    </div>
</main>
</body>
</html>
