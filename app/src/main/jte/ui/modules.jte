@import org.zalava.ui.ModulesController.ModulesModel
@import org.zalava.ui.ModulesController.ModuleEntry
@import org.zalava.ui.ModulesController.EnabledModuleEntry
@import org.zalava.ui.ModulesController.CatalogModuleEntry
@import org.zalava.ui.ModulesController.InstallationRequestEntry
@param ModulesModel model
@param String marketplaceMessage
@param String marketplaceError
@param org.zalava.ui.navigation.NavigationModel navigation
@param org.springframework.security.web.csrf.CsrfToken csrf

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>SEA Modules</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
    <link rel="stylesheet" href="/sea-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "modules")
<main class="section">
    <div class="container">
        <header class="modules-heading mb-5">
            <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-1">Default workspace</p>
            <h1 class="title is-3 mb-2">Modules</h1>
            <p class="has-text-grey">Installed capabilities and the catalog SEA can install from.</p>
        </header>

        @if(marketplaceMessage != null)
            <article class="message is-success" role="status"><div class="message-body">${marketplaceMessage}</div></article>
        @endif
    @if(marketplaceError != null)
      <article class="message is-danger" role="alert"><div class="message-body">${marketplaceError}</div></article>
    @endif

    <section class="box module-list mb-5" aria-labelledby="sea-restart-heading">
      <h2 id="sea-restart-heading" class="title is-5 mb-1">Load installed modules</h2>
      <p class="has-text-grey is-size-7 mb-3" data-sea-restart-status="${model.restart().phase()}">${model.restart().message()}</p>
      @if(model.restart().available())
        <form method="post" action="/modules/restart">
          <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
          @if(model.restart().requested())
            <button class="button is-warning" type="submit" data-action="restart-sea" disabled>Restart SEA to load installed modules</button>
          @else
            <button class="button is-warning" type="submit" data-action="restart-sea">Restart SEA to load installed modules</button>
          @endif
        </form>
      @else
        <p class="has-text-grey is-size-7">Use your local service manager to restart SEA, then return here to configure and start the module.</p>
      @endif
    </section>

    <section class="box module-list mb-5" aria-labelledby="uploaded-module-installation-heading">
    <h2 id="uploaded-module-installation-heading" class="title is-5 mb-1">Upload a module JAR</h2>
    <p class="has-text-grey is-size-7 mb-4">SEA reads the module metadata and checksum from the JAR, then asks for installation approval. Uploading never starts the module.</p>
    <form method="post" action="/modules/upload-installations" enctype="multipart/form-data" class="columns is-multiline">
      <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
      <div class="column is-9"><label class="label is-small" for="module-jar">Module JAR</label><input class="input" id="module-jar" name="moduleJar" type="file" accept=".jar,application/java-archive" required></div>
      <div class="column is-3 is-flex is-align-items-flex-end"><button class="button is-link is-fullwidth" type="submit" data-action="upload-module">Upload for approval</button></div>
    </form>
  </section>

  <section class="box module-list mb-5" aria-labelledby="local-module-installation-heading">
    <h2 id="local-module-installation-heading" class="title is-5 mb-1">Install a local build</h2>
    <p class="has-text-grey is-size-7 mb-4">Prepare an approval request from an accepted local development candidate. The project must be under SEA's mounted module workspace and already built.</p>
    <form method="post" action="/modules/local-project-installations" class="columns is-multiline">
      <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
      <div class="column is-5"><label class="label is-small" for="local-project-directory">Mounted project directory</label><input class="input" id="local-project-directory" name="projectDirectory" required maxlength="2000" placeholder="/var/lib/zalava/modules/example"></div>
      <div class="column is-2"><label class="label is-small" for="local-module-id">Module id</label><input class="input" id="local-module-id" name="moduleId" required maxlength="160" placeholder="zalava-module-example"></div>
      <div class="column is-2"><label class="label is-small" for="local-module-version">Version</label><input class="input" id="local-module-version" name="version" required maxlength="160" placeholder="1.0.0"></div>
      <div class="column is-2"><label class="label is-small" for="local-development-request">Accepted development request</label><input class="input" id="local-development-request" name="developmentRequestId" required maxlength="128"></div>
      <div class="column is-1 is-flex is-align-items-flex-end"><button class="button is-link is-fullwidth" type="submit" data-action="prepare-local-module">Prepare</button></div>
    </form>
  </section>
  <section class="box module-list" aria-labelledby="loaded-modules-heading">
            <div class="level is-mobile mb-4">
                <div class="level-left">
                    <div>
                        <h2 id="loaded-modules-heading" class="title is-5 mb-1">Loaded modules</h2>
                        <p class="has-text-grey is-size-7">Runtime modules active in this process.</p>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-light" data-metric="loaded-modules">${model.loadedModules().size()}</span>
                </div>
            </div>

            @if(model.loadedModules().isEmpty())
                <div class="notification is-light">No modules are loaded.</div>
            @else
                <div class="module-entries">
                    @for(ModuleEntry module : model.loadedModules())
                        <article class="module-entry" data-module="${module.moduleId()}">
                            <div class="module-copy">
                                <div class="module-heading">
                                    <h3 class="title is-6 mb-1">${module.displayName()}</h3>
                                    <span class="tag ${module.statusClass()}">${module.statusLabel()}</span>
                                </div>
                                <p class="has-text-grey is-size-7 mb-2">${module.moduleId()} · ${module.version()}</p>
                                <p class="module-description">${module.description()}</p>
                                <p class="has-text-grey is-size-7 mt-2">${module.provenance()}</p>
                                <p class="mt-3">
                                    <a class="tag is-link is-light" href="/modules/${module.moduleId()}"
                                        data-module-detail-link="${module.moduleId()}">Open module</a>
                                </p>
                            </div>
                            <div class="module-meta has-text-right">
                                <p class="heading mb-1">Providers</p>
                                <p class="title is-5 mb-0" data-provider-count="${module.moduleId()}">${module.providerCount()}</p>
                            </div>
                        </article>
                    @endfor
                </div>
            @endif
        </section>

        <section class="box module-list mt-5" aria-labelledby="enabled-modules-heading">
            <div class="level is-mobile mb-4">
                <div class="level-left">
                    <div>
                        <h2 id="enabled-modules-heading" class="title is-5 mb-1">Enabled external modules</h2>
                        <p class="has-text-grey is-size-7">Persisted enablement and provenance records.</p>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-light" data-metric="enabled-modules">${model.enabledModules().size()}</span>
                </div>
            </div>

            @if(model.enabledModules().isEmpty())
                <div class="notification is-light">No external modules are enabled yet.</div>
            @else
                <div class="module-entries">
                    @for(EnabledModuleEntry module : model.enabledModules())
                        <article class="module-entry" data-enabled-module="${module.moduleId()}">
                            <div class="module-copy">
                                <div class="module-heading">
                                    <h3 class="title is-6 mb-1">${module.moduleId()}</h3>
                                    <span class="tag is-info is-light">Enabled</span>
                                </div>
                                <p class="has-text-grey is-size-7 mb-2">${module.version()}</p>
                                @if(module.sourceRepository() != null && !module.sourceRepository().isBlank())
                                    <p class="module-description">${module.sourceRepository()}</p>
                                @else
                                    @if(module.binaryRepositoryId() != null && !module.binaryRepositoryId().isBlank())
                                        <p class="module-description">${module.binaryRepositoryId()}</p>
                                    @else
                                        <p class="module-description">Local artifact</p>
                                    @endif
                                @endif
                                <p class="mt-3">
                                    <a class="tag is-link is-light" href="/modules/${module.moduleId()}"
                                       data-module-detail-link="${module.moduleId()}">Open module</a>
                                </p>
                            </div>
                            <div class="module-meta has-text-right">
                                <p class="heading mb-1">Permissions</p>
                                <p class="title is-5 mb-0">${module.declaredPermissions().size()}</p>
                            </div>
                        </article>
                    @endfor
                </div>
            @endif
        </section>

        <section class="box module-list mt-5" aria-labelledby="marketplace-heading">
            <div class="level is-mobile mb-4">
                <div class="level-left">
                    <div>
                        <h2 id="marketplace-heading" class="title is-5 mb-1">Marketplace</h2>
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
                    <span class="has-text-grey is-size-7 ml-3" data-catalog-refreshed-at>Refreshed ${model.catalogRefreshedAt()}</span>
                @endif
            </form>

            @if(model.catalogModules().isEmpty())
                <div class="notification is-light" data-marketplace-empty>No catalog is loaded. Check the catalog to list available modules.</div>
            @else
                <div class="module-entries">
                    @for(CatalogModuleEntry module : model.catalogModules())
                        <article class="module-entry" data-catalog-module="${module.moduleId()}">
                            <div class="module-copy">
                                <div class="module-heading">
                                    <h3 class="title is-6 mb-1">${module.displayName()}</h3>
                                    @if(module.installed())
                                        <span class="tag is-info is-light" data-catalog-installed>Installed ${module.installedVersion()}</span>
                                    @else
                                        <span class="tag is-light">Not installed</span>
                                    @endif
                                </div>
                                <p class="has-text-grey is-size-7 mb-2">${module.moduleId()}</p>
                                <p class="module-description">${module.description()}</p>
                                <p class="mt-3">
                                    <a class="tag is-link is-light" href="/modules/${module.moduleId()}"
                                       data-catalog-detail-link="${module.moduleId()}">Releases</a>
                                </p>
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
                        <h2 id="installation-requests-heading" class="title is-5 mb-1">Installation requests</h2>
                        <p class="has-text-grey is-size-7">Prepared releases awaiting an explicit decision.</p>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-light" data-metric="installation-requests">${model.installationRequests().size()}</span>
                </div>
            </div>

            @if(model.installationRequests().isEmpty())
                <div class="notification is-light">No installation requests yet.</div>
            @else
                <div class="module-entries">
                    @for(InstallationRequestEntry request : model.installationRequests())
                        <article class="module-entry" data-installation-request="${request.requestId()}">
                            <div class="module-copy">
                                <div class="module-heading">
                                    <h3 class="title is-6 mb-1">${request.moduleId()} ${request.version()}</h3>
                                    <span class="tag ${request.statusClass()}" data-request-status>${request.status()}</span>
                                </div>
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
    </div>
</main>
</body>
</html>
