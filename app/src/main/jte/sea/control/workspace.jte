@import org.zalava.web.ui.SeaControlUiController.ModuleEntry
@import org.zalava.web.ui.SeaControlUiController.ProviderEntry
@import org.zalava.web.ui.SeaControlUiController.ScopeEntry
@import org.zalava.web.ui.SeaControlUiController.SeaControlUiModel
@import org.zalava.web.ui.SeaControlUiController.ToolEntry
@import org.zalava.web.ui.SeaControlUiController.VerificationEntry
@import org.zalava.web.ui.SeaControlUiController.VerificationStepEntry
@import org.zalava.web.ui.SeaControlUiController.DevelopmentRequestEntry
@import org.zalava.web.ui.SeaControlUiController.CandidateEntry
@import org.zalava.modules.development.CandidateEvaluation.Invocation
@import org.zalava.web.control.application.port.in.InvocationLogQueries.Entry
@import org.zalava.capabilities.approval.SeaToolApprovalRequests.Decision
@import org.zalava.modules.catalog.LocalArtifactInstallRequest
@import org.zalava.modules.catalog.ModuleReleaseInstallRequest

@param SeaControlUiModel model

<section class="d-flex flex-column flex-lg-row align-items-lg-end justify-content-between gap-3 mb-4">
                    <div>
        <p class="text-primary fw-bold text-uppercase small mb-1">Runtime</p>
        <h1 class="mb-0">SEA provider status</h1>
    </div>
    <div class="text-body-secondary small">Refreshed ${model.refreshedAt()}</div>
</section>

@if(model.installationError() != null)
    <div class="alert alert-danger" role="alert">${model.installationError()}</div>
@endif

<section class="mb-4" id="module-development">
    <div class="d-flex align-items-center justify-content-between gap-2 mb-3">
        <div><h2 class="h4 mb-1">External module development</h2><p class="text-body-secondary small mb-0">Prepare a request, export it for manual Codex work, submit a trusted candidate, then use a separate installation approval workflow.</p></div>
    </div>
    <div class="card shadow-sm mb-3"><div class="card-body">
        <form hx-post="/sea/control/development-requests" hx-target="#sea-control" hx-swap="innerHTML">
            <div class="row g-3"><div class="col-12 col-lg-4"><label class="form-label" for="development-reason">Reason</label><input class="form-control" id="development-reason" name="reason" required maxlength="2000"></div>
            <div class="col-12 col-lg-8"><label class="form-label" for="development-contract">Authoritative contract JSON</label><textarea class="form-control font-monospace" id="development-contract" name="contractJson" required maxlength="50000" rows="5" spellcheck="false"></textarea></div>
            <div class="col-12"><button class="btn btn-primary" type="submit">Create development request</button></div></div>
        </form>
    </div></div>
    <div class="card shadow-sm mb-3"><div class="card-body">
        <form hx-post="/sea/control/development-requests/revise" hx-target="#sea-control" hx-swap="innerHTML">
            <div class="row g-3"><div class="col-12 col-lg-3"><label class="form-label" for="development-revise-id">Request id</label><input class="form-control" id="development-revise-id" name="requestId" required maxlength="128"></div><div class="col-12 col-lg-3"><label class="form-label" for="development-revise-reason">Revision reason</label><input class="form-control" id="development-revise-reason" name="reason" required maxlength="2000"></div><div class="col-12 col-lg-6"><label class="form-label" for="development-revise-contract">Replacement contract JSON</label><textarea class="form-control font-monospace" id="development-revise-contract" name="contractJson" required maxlength="50000" rows="3" spellcheck="false"></textarea></div><div class="col-12"><button class="btn btn-outline-primary" type="submit">Revise request</button></div></div>
        </form>
    </div></div>
    <div class="card shadow-sm mb-3"><div class="card-body"><div class="row g-3">
        <form class="col-12 col-lg-4" hx-post="/sea/control/development-requests/inspect" hx-target="#sea-control" hx-swap="innerHTML">
            <label class="form-label" for="development-inspect-id">Request id</label><div class="input-group"><input class="form-control" id="development-inspect-id" name="requestId" required maxlength="128"><button class="btn btn-outline-primary" type="submit">Inspect</button></div>
        </form>
        <form class="col-12 col-lg-4" hx-post="/sea/control/development-requests/export" hx-target="#sea-control" hx-swap="innerHTML">
            <label class="form-label" for="development-workspace">Export workspace path</label><input class="form-control mb-2" id="development-workspace" name="workspaceRoot" required maxlength="2000"><input class="form-control mb-2" name="requestId" required maxlength="128" placeholder="Request id"><button class="btn btn-outline-primary" type="submit">Export</button>
        </form>
        <form class="col-12 col-lg-4" hx-post="/sea/control/development-requests/begin" hx-target="#sea-control" hx-swap="innerHTML">
            <label class="form-label" for="development-begin-id">Manual Codex handoff</label><input class="form-control mb-2" id="development-begin-id" name="requestId" required maxlength="128" placeholder="Request id"><button class="btn btn-outline-primary" type="submit">Record development started</button>
        </form>
        <form class="col-12 col-lg-4" hx-post="/sea/control/development-requests/candidate" hx-target="#sea-control" hx-swap="innerHTML">
            <label class="form-label" for="development-candidate">Trusted candidate JAR path</label><input class="form-control mb-2" id="development-candidate" name="artifactPath" required maxlength="2000"><input class="form-control mb-2" name="requestId" required maxlength="128" placeholder="Request id"><button class="btn btn-outline-primary" type="submit">Submit candidate</button>
        </form>
    </div></div></div>
    @if(model.developmentRequest() != null)
        !{DevelopmentRequestEntry request = model.developmentRequest();}
        <article class="card shadow-sm"><div class="card-body"><div class="d-flex flex-wrap align-items-center gap-2 mb-2"><h3 class="h5 mb-0">Development request</h3><code>${request.requestId()}</code><span class="badge text-bg-secondary">${request.status()}</span></div>
            @if(request.moduleId() != null)<p class="small mb-2">Module <code>${request.moduleId()}</code>, revision ${request.revision()}</p>@endif
            @if(!request.exportedFiles().isEmpty())<p class="small mb-2">SEA-owned files exported: ${request.exportedFiles().size()}</p>@endif
            <p class="small text-body-secondary">Installation approval: ${request.installationApproval()}.</p>
            @for(CandidateEntry candidate : request.candidates())
                <div class="border rounded p-2 mb-2"><div class="d-flex flex-wrap gap-2"><strong>Attempt ${candidate.number()}</strong><code>${candidate.digest()}</code><span class="badge text-bg-light border">${candidate.accepted()}</span></div><ul class="small mb-2">@for(String evidence : candidate.evidence())<li>${evidence}</li>@endfor</ul>@if(!candidate.invocations().isEmpty())<details class="mb-2"><summary class="small">Requested tool invocation</summary>@for(Invocation invocation : candidate.invocations())<dl class="row small mt-2 mb-0"><dt class="col-sm-2">Tool</dt><dd class="col-sm-10"><code>${invocation.toolName()}</code></dd><dt class="col-sm-2">Input</dt><dd class="col-sm-10"><pre class="small mb-1"><code>${invocation.inputJson()}</code></pre></dd><dt class="col-sm-2">Result</dt><dd class="col-sm-10"><span class="badge text-bg-secondary">${invocation.success()}</span> in ${invocation.elapsedMillis()} ms<pre class="small mt-1 mb-0"><code>${invocation.responseJson()}</code></pre></dd></dl>@endfor</details>@endif<details><summary class="small">Evaluation reports</summary><pre class="small"><code>${candidate.jsonReport()}</code></pre><pre class="small"><code>${candidate.markdownReport()}</code></pre></details></div>
            @endfor
        </div></article>
    @endif
</section>

<section class="mb-4" id="module-release-installations">
    <div class="d-flex align-items-center justify-content-between gap-2 mb-3">
        <div><h2 class="h4 mb-1">Catalog module installation</h2><p class="text-body-secondary small mb-0">Select a catalog module and release. SEA resolves its immutable manifest and package repository before approval.</p></div>
        <span class="badge text-bg-secondary">${model.moduleReleaseInstallationRequests().size()}</span>
    </div>
    <div class="card shadow-sm mb-3"><div class="card-body">
    <form hx-post="/sea/control/module-release-installations" hx-target="#sea-control" hx-swap="innerHTML">
      <div class="row g-3">
        <div class="col-12"><button class="btn btn-outline-primary btn-sm" type="button" hx-post="/sea/control/module-release-installations/catalog/refresh" hx-target="#sea-control" hx-swap="innerHTML">Refresh catalog</button>@if(model.catalogInstallation().refreshedAt() != null)<span class="text-body-secondary small ms-2">Refreshed ${model.catalogInstallation().refreshedAt()}</span>@endif</div>
        <div class="col-12 col-lg-4"><label class="form-label" for="release-module-id">Catalog module</label><select class="form-select" id="release-module-id" name="moduleId" required hx-post="/sea/control/module-release-installations/catalog/select" hx-trigger="change" hx-target="#sea-control" hx-swap="innerHTML"><option value="">Choose a module</option>@for(var module : model.catalogInstallation().modules())@if(module.moduleId().equals(model.catalogInstallation().selectedModuleId()))<option value="${module.moduleId()}" selected>${module.displayName()} (${module.moduleId()})</option>@else<option value="${module.moduleId()}">${module.displayName()} (${module.moduleId()})</option>@endif@endfor</select></div>
        <div class="col-12 col-lg-4"><label class="form-label" for="release-version">Release version</label><select class="form-select" id="release-version" name="version" required><option value="">Choose a release</option>@for(var release : model.catalogInstallation().releases())<option value="${release.version()}">${release.version()} (${release.releaseTag()})</option>@endfor</select></div>
                <div class="col-12 col-lg-4"><label class="form-label" for="release-development-request-id">Development request id <span class="text-body-secondary">(optional)</span></label><input class="form-control" id="release-development-request-id" name="developmentRequestId" maxlength="160"></div>
        <div class="col-12"><button class="btn btn-primary" type="submit">Prepare binary release request</button></div>
            </div>
        </form>
    </div></div>
    <div class="vstack gap-2">
    @if(model.moduleReleaseInstallationRequests().isEmpty())
        <p class="text-body-secondary">No binary release installation requests recorded.</p>
    @else
        @for(ModuleReleaseInstallRequest request : model.moduleReleaseInstallationRequests())
            <article class="card shadow-sm"><div class="card-body"><div class="d-flex flex-column flex-lg-row justify-content-between gap-3"><div><div class="d-flex flex-wrap align-items-center gap-2 mb-1"><h3 class="h6 mb-0">${request.module().displayName()}</h3><code>${request.module().moduleId()}</code><span class="badge text-bg-light border">${request.module().version()}</span></div><div class="small text-body-secondary">Request <code>${request.requestId()}</code> created <code>${request.createdAt().toString()}</code></div></div><div><span class="badge text-bg-secondary">${request.status()}</span>@if(request.status() == ModuleReleaseInstallRequest.Status.PENDING)<div class="mt-2 d-flex gap-2"><button class="btn btn-sm btn-success" hx-post="/sea/control/module-release-installations/${request.requestId()}/allow" hx-target="#sea-control" hx-swap="innerHTML">Allow</button><button class="btn btn-sm btn-outline-danger" hx-post="/sea/control/module-release-installations/${request.requestId()}/deny" hx-target="#sea-control" hx-swap="innerHTML">Deny</button></div>@endif</div></div><div class="row g-3 mt-1 small"><div class="col-12 col-lg-5"><div class="text-body-secondary">Immutable manifest</div><a class="text-break" href="${request.manifestUri().toString()}" rel="noreferrer">${request.manifestUri().toString()}</a></div><div class="col-12 col-lg-4"><div class="text-body-secondary">Artifact</div><code class="text-break">${request.module().artifact().groupId()}:${request.module().artifact().artifactId()}:${request.module().artifact().version()}</code><code class="d-block text-break">${request.artifactDigest()}</code></div><div class="col-12 col-lg-3"><div class="text-body-secondary">Repository</div><code>${request.repositoryId()}</code></div></div><p class="small text-body-secondary mb-0 mt-3">${request.message()}</p></div></article>
        @endfor
    @endif
    </div>
</section>

<section class="mb-4" id="local-module-installations">
    <div class="d-flex align-items-center justify-content-between gap-2 mb-3">
        <div><h2 class="h4 mb-1">Local artifact installation</h2><p class="text-body-secondary small mb-0">Create a reviewed request for a trusted local JAR.</p></div>
        <span class="badge text-bg-secondary">${model.localArtifactInstallationRequests().size()}</span>
    </div>
    <div class="card shadow-sm mb-3"><div class="card-body">
        <form class="mb-3" hx-post="/sea/control/local-module-project-installations" hx-target="#sea-control" hx-swap="innerHTML">
            <div class="row g-3"><div class="col-12 col-lg-5"><label class="form-label" for="local-project-directory">Built module project directory</label><input class="form-control" id="local-project-directory" name="projectDirectory" required></div>
            <div class="col-12 col-lg-3"><label class="form-label" for="local-project-module-id">Module id</label><input class="form-control" id="local-project-module-id" name="moduleId" required maxlength="160"></div>
            <div class="col-12 col-lg-2"><label class="form-label" for="local-project-version">Version</label><input class="form-control" id="local-project-version" name="version" required maxlength="160"></div>
            <div class="col-12 col-lg-2"><label class="form-label" for="local-project-development-request">Development request id (optional)</label><input class="form-control" id="local-project-development-request" name="developmentRequestId" maxlength="160"></div>
            <div class="col-12"><button class="btn btn-primary" type="submit">Prepare built local module</button></div></div>
        </form>
        <form hx-post="/sea/control/local-module-installations" hx-target="#sea-control" hx-swap="innerHTML">
            <div class="row g-3"><div class="col-12 col-lg-3"><label class="form-label" for="local-module-id">Module id</label><input class="form-control" id="local-module-id" name="moduleId" required maxlength="160"></div>
            <div class="col-12 col-lg-3"><label class="form-label" for="local-development-request-id">Development request id</label><input class="form-control" id="local-development-request-id" name="developmentRequestId" required maxlength="160"></div>
            <div class="col-12 col-lg-4"><label class="form-label" for="local-module-path">Local JAR path</label><input class="form-control" id="local-module-path" name="artifactPath" required></div>
            <div class="col-12 col-lg-5"><label class="form-label" for="local-module-index">Local module metadata YAML</label><textarea class="form-control font-monospace" id="local-module-index" name="indexYaml" required rows="4"></textarea></div>
            <div class="col-12"><button class="btn btn-primary" type="submit">Create local artifact request</button></div></div>
        </form>
    </div></div>
    <div class="vstack gap-2">
    @if(model.localArtifactInstallationRequests().isEmpty())
        <p class="text-body-secondary">No local artifact installation requests recorded.</p>
    @else
        @for(LocalArtifactInstallRequest request : model.localArtifactInstallationRequests())
            <article class="card shadow-sm"><div class="card-body"><div class="d-flex justify-content-between gap-3"><div><h3 class="h6 mb-1">${request.module().displayName()}</h3><code>${request.module().moduleId()}</code><p class="small mb-1">${request.artifactPath()}</p><code class="small">${request.artifactDigest()}</code></div><div><span class="badge text-bg-secondary">${request.status()}</span>
            @if(request.status() == LocalArtifactInstallRequest.Status.PENDING)<div class="mt-2 d-flex gap-2"><button class="btn btn-sm btn-success" hx-post="/sea/control/local-module-installations/${request.requestId()}/allow" hx-target="#sea-control" hx-swap="innerHTML">Allow</button><button class="btn btn-sm btn-outline-danger" hx-post="/sea/control/local-module-installations/${request.requestId()}/deny" hx-target="#sea-control" hx-swap="innerHTML">Deny</button></div>@endif
            </div></div><p class="small text-body-secondary mb-0 mt-2">${request.message()}</p></div></article>
        @endfor
    @endif
    </div>
</section>

<section class="row g-3 mb-4" aria-label="Runtime summary">
    <div class="col-6 col-xl-3">
        <div class="metric-panel bg-body border rounded-2 p-3">
            <div class="text-body-secondary small">Modules</div>
            <div class="fs-3 fw-semibold">${model.moduleCount()}</div>
        </div>
    </div>
    <div class="col-6 col-xl-3">
        <div class="metric-panel bg-body border rounded-2 p-3">
            <div class="text-body-secondary small">Providers</div>
            <div class="fs-3 fw-semibold">${model.providerCount()}</div>
        </div>
    </div>
    <div class="col-6 col-xl-3">
        <div class="metric-panel bg-body border rounded-2 p-3">
            <div class="text-body-secondary small">Tools</div>
            <div class="fs-3 fw-semibold">${model.toolCount()}</div>
        </div>
    </div>
    <div class="col-6 col-xl-3">
        <div class="metric-panel bg-body border rounded-2 p-3">
            <div class="text-body-secondary small">Side-effecting</div>
            <div class="fs-3 fw-semibold">${model.sideEffectingToolCount()}</div>
        </div>
    </div>
</section>


<section class="mb-4">
    <div class="d-flex align-items-center justify-content-between gap-2 mb-3">
        <h2 class="h4 mb-0">Permission policies</h2>
        <span class="badge text-bg-secondary">${model.permissionPolicies().size()}</span>
    </div>

    <div class="vstack gap-3">
        @if(model.permissionPolicies().isEmpty())
            <div class="card shadow-sm">
                <p class="text-body-secondary mb-0 p-3">No tool approval policies are active.</p>
            </div>
        @else
            @for(var policy : model.permissionPolicies())
                <article class="card shadow-sm">
                    <div class="card-body">
                        <div class="d-flex flex-column flex-lg-row justify-content-between gap-3 mb-3">
                            <div>
                                <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
                                    <h3 class="h6 mb-0">${policy.toolName()}</h3>
                                    <code class="small">${policy.providerId()}</code>
                                    <span class="badge text-bg-success">always allow tool</span>
                                </div>
                                <div class="text-body-secondary small">
                                    Actor ${policy.actorId()} · policy request <code>${policy.requestId()}</code>
                                </div>
                                <p class="small mt-2 mb-0">${policy.summary().allowToolPolicy()}</p>
                    <div class="text-body-secondary small">
                        Created <code>${policy.createdAt()}</code>
                        @if(policy.decidedAt() != null)
                            · allowed <code>${policy.decidedAt()}</code>
                        @endif
                    </div>
                    <div class="text-body-secondary small">
                        Module <code>${policy.attributes().getOrDefault("permissionPolicyModule", "unknown")}</code>
                        · channel <code>${policy.attributes().getOrDefault("permissionPolicyChannel", "unknown")}</code>
                        @if(policy.attributes().containsKey("permissionPolicyExpiresAt"))
                            · expires <code>${policy.attributes().get("permissionPolicyExpiresAt")}</code>
                        @endif
                    </div>
                            </div>
                            <div>
                                <button class="btn btn-sm btn-outline-danger"
                                        type="button"
                                        hx-post="/sea/control/permission-policies/${policy.requestId()}/revoke"
                                        hx-target="#sea-control"
                                        hx-swap="innerHTML">
                                    Revoke
                                </button>
                            </div>
                        </div>
                        <div class="row g-3">
                            <div class="col-12 col-lg-6">
                                <div class="small text-body-secondary mb-1">Scope</div>
                                <dl class="row small mb-0">
                                    @for(var scope : policy.summary().scope().entrySet())
                                        <dt class="col-sm-4 text-body-secondary">${scope.getKey()}</dt>
                                        <dd class="col-sm-8"><code>${scope.getValue()}</code></dd>
                                    @endfor
                                </dl>
                            </div>
                            <div class="col-12 col-lg-6">
                                <div class="small text-body-secondary mb-1">Policy tags</div>
                                <div class="d-flex flex-wrap gap-1">
                                    <span class="badge text-bg-warning">${policy.summary().effect()}</span>
                                    @for(String tag : policy.summary().policyTags())
                                        <span class="badge text-bg-light border">${tag}</span>
                                    @endfor
                    </div>
                </div>
                <form class="row g-2 mt-2" hx-post="/sea/control/permission-policies/${policy.requestId()}/narrow" hx-target="#sea-control" hx-swap="innerHTML">
                    <div class="col-12 col-lg-3"><input class="form-control form-control-sm" name="scopeKey" maxlength="100" placeholder="Additional scope key"></div>
                    <div class="col-12 col-lg-3"><input class="form-control form-control-sm" name="scopeValue" maxlength="500" placeholder="Additional scope value"></div>
                    <div class="col-12 col-lg-4"><input class="form-control form-control-sm" name="expiresAt" maxlength="64" placeholder="Expiry UTC (ISO-8601)"></div>
                    <div class="col-12 col-lg-2"><button class="btn btn-sm btn-outline-primary w-100" type="submit">Narrow policy</button></div>
                </form>
            </div>
                    </div>
                </article>
            @endfor
        @endif
    </div>
</section>

<section class="mb-4">
    <div class="d-flex align-items-center justify-content-between gap-2 mb-3">
        <h2 class="h4 mb-0">Permission requests</h2>
        <span class="badge text-bg-secondary">${model.permissionRequests().size()}</span>
    </div>

    <div class="vstack gap-3">
        @if(model.permissionRequests().isEmpty())
            <div class="card shadow-sm">
                <p class="text-body-secondary mb-0 p-3">No permission requests recorded in this runtime.</p>
            </div>
        @else
            @for(var request : model.permissionRequests())
                <article class="card shadow-sm">
                    <div class="card-body">
                        <div class="d-flex flex-column flex-lg-row justify-content-between gap-3 mb-3">
                            <div>
                                <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
                                    <h3 class="h6 mb-0">${request.toolName()}</h3>
                                    <code class="small">${request.providerId()}</code>
                                    @if(request.decision() == Decision.PENDING)
                                        <span class="badge text-bg-warning">pending</span>
                                    @elseif(request.decision() == Decision.ALLOWED)
                                        <span class="badge text-bg-success">allowed</span>
                                    @elseif(request.decision() == Decision.DENIED)
                                        <span class="badge text-bg-danger">denied</span>
                                    @else
                                        <span class="badge text-bg-secondary">revoked</span>
                                    @endif
                                </div>
                                <div class="text-body-secondary small">
                                    Requested by ${request.actorId()} at <code>${request.createdAt()}</code>
                                </div>
                                <p class="small mt-2 mb-0">${request.summary().prompt()}</p>
                                <div class="text-body-secondary small">
                                    Request <code>${request.requestId()}</code>
                                    @if(request.decidedAt() != null)
                                        decided at <code>${request.decidedAt()}</code>
                                    @endif
                                </div>
                            </div>
                            @if(request.decision() == Decision.PENDING && request.taskReference() == null)
                                <div class="d-flex flex-wrap gap-2">
                                    <button class="btn btn-sm btn-success"
                                            type="button"
                                            hx-post="/api/sea/permission-requests/${request.requestId()}/allow"
                                            hx-swap="none"
                                            hx-on::after-request="htmx.ajax('GET', '/sea/control/workspace', {target:'#sea-control', swap:'innerHTML'})">
                                        Allow
                                    </button>
                                    <span class="visually-hidden">${request.summary().allowOnceDecision()}</span>
                                    <button class="btn btn-sm btn-outline-success"
                                            type="button"
                                            hx-post="/api/sea/permission-requests/${request.requestId()}/allow-tool"
                                            hx-swap="none"
                                            hx-on::after-request="htmx.ajax('GET', '/sea/control/workspace', {target:'#sea-control', swap:'innerHTML'})">
                                        Always allow tool
                                    </button>
                                    <span class="visually-hidden">${request.summary().allowToolPolicy()}</span>
                                    <button class="btn btn-sm btn-outline-danger"
                                            type="button"
                                            hx-post="/api/sea/permission-requests/${request.requestId()}/deny"
                                            hx-swap="none"
                                            hx-on::after-request="htmx.ajax('GET', '/sea/control/workspace', {target:'#sea-control', swap:'innerHTML'})">
                                        Deny
                                    </button>
                                    <span class="visually-hidden">${request.summary().denyDecision()}</span>
                                </div>
                            @elseif(request.decision() == Decision.PENDING)
                                <a class="btn btn-sm btn-primary" href="/jobs/${request.taskReference()}">
                                    Review job approval
                                </a>
                            @endif
                        </div>

                        <div class="row g-3">
                            <div class="col-12 col-lg-6">
                                <div class="small text-body-secondary mb-1">Arguments preview</div>
                                <pre class="verification-body small mb-0"><code>${request.summary().argumentsPreview()}</code></pre>
                            </div>
                            <div class="col-12 col-lg-6">
                                <div class="small text-body-secondary mb-1">Scope and policy</div>
                                <dl class="row small mb-2">
                                    @for(var scope : request.summary().scope().entrySet())
                                        <dt class="col-sm-4 text-body-secondary">${scope.getKey()}</dt>
                                        <dd class="col-sm-8"><code>${scope.getValue()}</code></dd>
                                    @endfor
                                </dl>
                                <div class="d-flex flex-wrap gap-1">
                                    <span class="badge text-bg-warning">${request.summary().effect()}</span>
                                    @for(String tag : request.summary().policyTags())
                                        <span class="badge text-bg-light border">${tag}</span>
                                    @endfor
                                </div>
                            </div>
                        </div>
                    </div>
                </article>
            @endfor
        @endif
    </div>
</section>

<section class="mb-4">
    <div class="d-flex align-items-center justify-content-between gap-2 mb-3">
        <h2 class="h4 mb-0">Recent provider calls</h2>
        <span class="badge text-bg-secondary">${model.invocationLogs().size()}</span>
    </div>

    <div class="card shadow-sm">
        <div class="card-body p-0">
            @if(model.invocationLogs().isEmpty())
                <p class="text-body-secondary mb-0 p-3">No provider calls recorded in this runtime.</p>
            @else
                <div class="table-responsive">
                    <table class="table table-sm align-middle mb-0">
                        <thead>
                        <tr>
                            <th scope="col">Time</th>
                            <th scope="col">Provider</th>
                            <th scope="col">Tool</th>
                            <th scope="col">Actor</th>
                            <th scope="col">Status</th>
                            <th scope="col">Duration</th>
                            <th scope="col">Result</th>
                            <th scope="col">Effect</th>
                            <th scope="col">Policy</th>
                        </tr>
                        </thead>
                        <tbody>
                        @for(Entry entry : model.invocationLogs())
                            <tr>
                                <td><code class="small">${entry.timestamp()}</code></td>
                                <td><code class="small">${entry.providerId()}</code></td>
                                <td>
                                    <div class="fw-semibold">${entry.toolName()}</div>
                                    <span class="badge text-bg-info">${entry.classification()}</span>
                                </td>
                                <td>
                                    <div>${entry.actorId()}</div>
                                    @if(entry.confirmed())
                                        <span class="badge text-bg-danger">confirmed</span>
                                    @else
                                        <span class="badge text-bg-light border">unconfirmed</span>
                                    @endif
                                </td>
                                <td>
                                    @if(entry.success())
                                        <span class="badge text-bg-success">success</span>
                                    @else
                                        <span class="badge text-bg-danger">failed</span>
                                        @if(entry.errorType() != null)
                                            <code class="d-block small mt-1">${entry.errorType()}</code>
                                        @endif
                                        @if(entry.errorMessage() != null)
                                            <div class="small text-body-secondary mt-1">${entry.errorMessage()}</div>
                                        @endif
                                    @endif
                                </td>
                                <td>${entry.durationMillis()} ms</td>
                                <td>
                                    @if(entry.resultPreview() != null)
                                        <pre class="invocation-result small mb-0"><code>${entry.resultPreview()}</code></pre>
                                    @else
                                        <span class="text-body-secondary small">No result preview</span>
                                    @endif
                                </td>
                                <td>
                                    @if(entry.sideEffecting())
                                        <span class="badge text-bg-warning">side-effecting</span>
                                    @else
                                        <span class="badge text-bg-success">read-only</span>
                                    @endif
                                </td>
                                <td>
                                    <div class="d-flex flex-wrap gap-1">
                                        @for(String tag : entry.policyTags())
                                            <span class="badge text-bg-light border">${tag}</span>
                                        @endfor
                                    </div>
                                </td>
                            </tr>
                        @endfor
                        </tbody>
                    </table>
                </div>
            @endif
        </div>
    </div>
</section>

<section class="row g-4 align-items-start">
    <section class="col-12 col-xl-8">
        <div class="d-flex align-items-center justify-content-between gap-2 mb-3">
            <h2 class="h4 mb-0">Providers</h2>
            <span class="badge text-bg-secondary">${model.providers().size()}</span>
        </div>

        <div class="vstack gap-3">
            @for(ProviderEntry provider : model.providers())
                <article class="card shadow-sm">
                    <div class="card-body">
                        <div class="d-flex flex-column flex-lg-row align-items-lg-start justify-content-between gap-3 mb-3">
                            <div>
                                <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
                                    <h3 class="h5 mb-0">${provider.displayName()}</h3>
                                    <code class="small">${provider.providerId()}</code>
                                </div>
                                <div class="text-body-secondary small">${provider.moduleId()} / ${provider.providerType()}</div>
                            </div>
                            <div class="d-flex flex-wrap gap-1">
                                @for(String tag : provider.policyTags())
                                    <span class="badge text-bg-light border">${tag}</span>
                                @endfor
                            </div>
                        </div>

                        <p class="text-body-secondary mb-3">${provider.description()}</p>

                        @if(!provider.scope().isEmpty())
                            <dl class="row small mb-3">
                                @for(ScopeEntry scope : provider.scope())
                                    <dt class="col-sm-3 text-body-secondary">${scope.name()}</dt>
                                    <dd class="col-sm-9"><code>${scope.value()}</code></dd>
                                @endfor
                            </dl>
                        @endif

                        <div class="table-responsive">
                            <table class="table table-sm align-middle mb-0">
                                <thead>
                                <tr>
                                    <th scope="col">Tool</th>
                                    <th scope="col">Classification</th>
                                    <th scope="col">Effect</th>
                                    <th scope="col">Policy</th>
                                </tr>
                                </thead>
                                <tbody>
                                @for(ToolEntry tool : provider.tools())
                                    <tr>
                                        <td>
                                            <div class="fw-semibold">${tool.name()}</div>
                                            <div class="text-body-secondary small">${tool.description()}</div>
                                        </td>
                                        <td><span class="badge text-bg-info">${tool.classification()}</span></td>
                                        <td>
                                            @if(tool.sideEffecting())
                                                <span class="badge text-bg-warning">side-effecting</span>
                                            @else
                                                <span class="badge text-bg-success">read-only</span>
                                            @endif
                                        </td>
                                        <td>
                                            <div class="d-flex flex-wrap gap-1">
                                                @for(String tag : tool.policyTags())
                                                    <span class="badge text-bg-light border">${tag}</span>
                                                @endfor
                                            </div>
                                        </td>
                                    </tr>
                                @endfor
                                </tbody>
                            </table>
                        </div>
                    </div>
                </article>
            @endfor
        </div>
    </section>

    <aside class="col-12 col-xl-4">
        <section class="mb-4">
            <div class="d-flex align-items-center justify-content-between gap-2 mb-3">
                <h2 class="h4 mb-0">Bootstrap Verification</h2>
                <span class="badge text-bg-secondary">${model.verifications().size()}</span>
            </div>

            <div class="vstack gap-3">
                @for(int verificationIndex = 0; verificationIndex < model.verifications().size(); verificationIndex++)
                    !{VerificationEntry verification = model.verifications().get(verificationIndex);}
                    <article class="card shadow-sm">
                        <div class="card-body">
                            <div class="d-flex align-items-start justify-content-between gap-3 mb-2">
                                <div>
                                    <h3 class="h6 mb-1">${verification.toolset()}</h3>
                                    <code class="small">${verification.providerId()}</code>
                                </div>
                                @if(verification.available())
                                    <span class="badge text-bg-success">available</span>
                                @else
                                    <span class="badge text-bg-danger">missing</span>
                                @endif
                            </div>
                            @if(verification.missingReason() != null)
                                <p class="text-danger small mb-3">${verification.missingReason()}</p>
                            @endif

                            <div class="accordion" id="verification-${verificationIndex + 1}">
                                @for(int stepIndex = 0; stepIndex < verification.steps().size(); stepIndex++)
                                    !{VerificationStepEntry step = verification.steps().get(stepIndex);}
                                    <div class="accordion-item">
                                        <h4 class="accordion-header">
                                            <button class="accordion-button collapsed py-2" type="button"
                                                    data-bs-toggle="collapse"
                                                    data-bs-target="#verification-${verificationIndex + 1}-step-${stepIndex + 1}"
                                                    aria-expanded="false"
                                                    aria-controls="verification-${verificationIndex + 1}-step-${stepIndex + 1}">
                                                ${step.label()}
                                            </button>
                                        </h4>
                                        <div id="verification-${verificationIndex + 1}-step-${stepIndex + 1}"
                                             class="accordion-collapse collapse"
                                             data-bs-parent="#verification-${verificationIndex + 1}">
                                            <div class="accordion-body">
                                                <div class="d-flex flex-wrap gap-1 mb-2">
                                                    <span class="badge text-bg-secondary">${step.method()}</span>
                                                    @if(step.sideEffecting())
                                                        <span class="badge text-bg-warning">side-effecting</span>
                                                    @else
                                                        <span class="badge text-bg-success">read-only</span>
                                                    @endif
                                                    @if(step.confirmationRequired())
                                                        <span class="badge text-bg-danger">confirmation</span>
                                                    @endif
                                                </div>
                                                <code class="d-block small mb-2">${step.path()}</code>
                                                <pre class="verification-body small mb-0"><code>${step.bodyJson()}</code></pre>
                                            </div>
                                        </div>
                                    </div>
                                @endfor
                            </div>
                        </div>
                    </article>
                @endfor
            </div>
        </section>

        <section>
            <div class="d-flex align-items-center justify-content-between gap-2 mb-3">
                <h2 class="h4 mb-0">Modules</h2>
                <span class="badge text-bg-secondary">${model.modules().size()}</span>
            </div>
            <div class="list-group shadow-sm">
                @for(ModuleEntry module : model.modules())
                    <div class="list-group-item">
                        <div class="d-flex align-items-center justify-content-between gap-2 mb-1">
                            <div class="fw-semibold">${module.displayName()}</div>
                            <span class="badge text-bg-light border">${module.version()}</span>
                        </div>
                        <code class="small">${module.moduleId()}</code>
                        <p class="text-body-secondary small mb-0 mt-1">${module.description()}</p>
                    </div>
                @endfor
            </div>
        </section>
    </aside>
</section>
