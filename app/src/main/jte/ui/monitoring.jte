@import org.zalava.ui.MonitoringController.CallEntry
@import org.zalava.ui.MonitoringController.JobEntry
@import org.zalava.ui.MonitoringController.MonitoringModel
@import org.zalava.ui.MonitoringController.ProviderEntry
@import org.zalava.ui.MonitoringController.RunEntry

@param MonitoringModel model
@param org.zalava.ui.navigation.NavigationModel navigation

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>SEA Monitoring</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
    <link rel="stylesheet" href="/sea-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "monitoring")

<main class="section">
    <div class="container">
        <header class="monitoring-heading mb-6">
            <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-2">Operations</p>
            <h1 class="title is-2 mb-3">Monitoring</h1>
            <p class="subtitle is-5 has-text-grey">
                Live work and the run, provider and tool evidence SEA already records.
            </p>
        </header>
        <p class="mb-5"><a class="button is-link is-light" href="/monitoring/logs">View SEA logs</a></p>

        <section class="box mb-5" data-monitoring-snapshot data-refreshed-at="${model.refreshedAt()}">
            <div class="level mb-4">
                <div class="level-left">
                    <div>
                        <p class="heading mb-1">Snapshot</p>
                        <h2 class="title is-4 mb-0">Operational summary</h2>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-rounded">Rendered ${model.refreshedAt()}</span>
                </div>
            </div>
            <div class="columns is-variable is-4">
                <div class="column">
                    <p class="heading">Live jobs</p>
                    <p class="title is-3" data-metric="live-jobs">${model.snapshot().liveJobs()}</p>
                </div>
                <div class="column">
                    <p class="heading">Recorded runs</p>
                    <p class="title is-3" data-metric="recorded-runs">${model.snapshot().recordedRuns()}</p>
                </div>
                <div class="column">
                    <p class="heading">Failed runs</p>
                    <p class="title is-3" data-metric="failed-runs">${model.snapshot().failedRuns()}</p>
                </div>
                <div class="column">
                    <p class="heading">Provider calls</p>
                    <p class="title is-3" data-metric="provider-calls">${model.snapshot().providerCalls()}</p>
                </div>
                <div class="column">
                    <p class="heading">Failed calls</p>
                    <p class="title is-3" data-metric="failed-provider-calls">${model.snapshot().failedProviderCalls()}</p>
                </div>
            </div>
            @if(model.viewerConfigured())
                <a class="button is-link is-light" href="${model.viewerUrl()}" rel="noopener" data-metrics-viewer>
                    Open metrics viewer
                </a>
            @endif
        </section>

        <section class="box dashboard-activity mb-5" data-monitoring-live>
            <div class="level mb-4">
                <div class="level-left">
                    <div>
                        <p class="heading mb-1">Live work</p>
                        <h2 class="title is-4 mb-0">Jobs in progress</h2>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-rounded">${model.liveJobs().size()} shown</span>
                </div>
            </div>
            @if(model.liveJobs().isEmpty())
                <div class="notification is-light has-text-centered py-5" data-empty="live-jobs">
                    <p class="has-text-weight-semibold mb-1">Nothing is running right now.</p>
                    <p class="has-text-grey">Queued, running and waiting jobs appear here.</p>
                </div>
            @else
                <div class="activity-list">
                    @for(JobEntry job : model.liveJobs())
                        <a class="activity-entry" href="${job.url()}" data-job>
                            <div class="activity-copy">
                                <h3 class="has-text-weight-semibold">${job.name()}</h3>
                                <p class="has-text-grey is-size-7">${job.detail()}</p>
                            </div>
                            <span class="tag ${job.statusClass()}">${job.statusLabel()}</span>
                        </a>
                    @endfor
                </div>
            @endif
        </section>

        <section class="box dashboard-activity mb-5" data-monitoring-terminal>
            <div class="level mb-4">
                <div class="level-left">
                    <div>
                        <p class="heading mb-1">Terminal work</p>
                        <h2 class="title is-4 mb-0">Recently finished jobs</h2>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-rounded">${model.terminalJobs().size()} shown</span>
                </div>
            </div>
            @if(model.terminalJobs().isEmpty())
                <div class="notification is-light has-text-centered py-5" data-empty="terminal-jobs">
                    <p class="has-text-weight-semibold mb-1">No finished jobs yet.</p>
                    <p class="has-text-grey">Completed, cancelled and failed jobs appear here.</p>
                </div>
            @else
                <div class="activity-list">
                    @for(JobEntry job : model.terminalJobs())
                        <a class="activity-entry" href="${job.url()}" data-job>
                            <div class="activity-copy">
                                <h3 class="has-text-weight-semibold">${job.name()}</h3>
                                <p class="has-text-grey is-size-7">${job.detail()}</p>
                            </div>
                            <span class="tag ${job.statusClass()}">${job.statusLabel()}</span>
                        </a>
                    @endfor
                </div>
            @endif
        </section>

        <section class="box dashboard-activity mb-5" data-monitoring-runs>
            <div class="level mb-4">
                <div class="level-left">
                    <div>
                        <p class="heading mb-1">Run evidence</p>
                        <h2 class="title is-4 mb-0">Recent agent runs</h2>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-rounded">${model.runs().size()} shown</span>
                </div>
            </div>
            @if(model.runs().isEmpty())
                <div class="notification is-light has-text-centered py-5" data-empty="runs">
                    <p class="has-text-weight-semibold mb-1">No agent runs recorded yet.</p>
                    <p class="has-text-grey">Completed and failed runs will appear here.</p>
                </div>
            @else
                <div class="activity-list">
                    @for(RunEntry run : model.runs())
                        <article class="activity-entry monitoring-run" data-run-id="${run.id()}" data-run-failed="${run.failed()}">
                            <div class="activity-copy">
                                <h3 class="has-text-weight-semibold mb-1">${run.promptTypeLabel()} run · ${run.startedAt()}</h3>
                                <p class="has-text-grey is-size-7 mb-2">
                                    ${run.durationLabel()} · context ${run.contextCharactersUsed()} / ${run.contextCharacterBudget()} characters
                                </p>
                                @if(run.promptPreview() != null && !run.promptPreview().isBlank())
                                    <p class="is-size-7"><strong>Prompt:</strong> ${run.promptPreview()}</p>
                                @endif
                                @if(run.failed() && run.errorPreview() != null && !run.errorPreview().isBlank())
                                    <p class="notification is-danger is-light py-2 my-2" data-run-error>${run.errorPreview()}</p>
                                @endif
                                @if(run.resultPreview() != null && !run.resultPreview().isBlank())
                                    <p class="is-size-7"><strong>Result:</strong> ${run.resultPreview()}</p>
                                @endif
                                @if(run.evidenceUrl() != null)
                                    <a class="is-size-7" href="${run.evidenceUrl()}" data-run-evidence>${run.evidenceLabel()}</a>
                                @endif
                            </div>
                            <span class="tag ${run.statusClass()}">${run.statusLabel()}</span>
                        </article>
                    @endfor
                </div>
            @endif
        </section>

        <section class="box dashboard-activity mb-5" data-monitoring-providers>
            <div class="level mb-4">
                <div class="level-left">
                    <div>
                        <p class="heading mb-1">Provider readiness</p>
                        <h2 class="title is-4 mb-0">Bootstrap verification</h2>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-rounded">${model.providers().size()} providers</span>
                </div>
            </div>
            @if(model.providers().isEmpty())
                <div class="notification is-light has-text-centered py-5" data-empty="providers">
                    <p class="has-text-weight-semibold mb-1">No providers loaded.</p>
                    <p class="has-text-grey">Loaded providers and their tool readiness appear here.</p>
                </div>
            @else
                <div class="columns is-multiline is-variable is-4">
                    @for(ProviderEntry provider : model.providers())
                        <div class="column is-half">
                            <article class="box" data-provider="${provider.providerId()}">
                                <div class="level is-mobile mb-2">
                                    <div class="level-left">
                                        <div>
                                            <h3 class="has-text-weight-semibold">${provider.displayName()}</h3>
                                            <p class="has-text-grey is-size-7">${provider.providerId()} · ${provider.toolset()}</p>
                                        </div>
                                    </div>
                                    <div class="level-right">
                                        <span class="tag ${provider.statusClass()}">${provider.statusLabel()}</span>
                                    </div>
                                </div>
                                @if(provider.missingReason() != null && !provider.missingReason().isBlank())
                                    <p class="has-text-grey is-size-7" data-provider-missing-reason>${provider.missingReason()}</p>
                                @endif
                            </article>
                        </div>
                    @endfor
                </div>
            @endif
        </section>

        <section class="box dashboard-activity" data-monitoring-calls>
            <div class="level mb-4">
                <div class="level-left">
                    <div>
                        <p class="heading mb-1">Provider evidence</p>
                        <h2 class="title is-4 mb-0">Recent tool calls</h2>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-rounded">${model.calls().size()} shown</span>
                </div>
            </div>
            @if(model.calls().isEmpty())
                <div class="notification is-light has-text-centered py-5" data-empty="provider-calls">
                    <p class="has-text-weight-semibold mb-1">No provider calls recorded yet.</p>
                    <p class="has-text-grey">Provider and tool executions will appear here.</p>
                </div>
            @else
                <div class="activity-list">
                    @for(CallEntry call : model.calls())
                        <article class="activity-entry monitoring-call" data-provider-call data-call-failed="${call.failed()}">
                            <div class="activity-copy">
                                <h3 class="has-text-weight-semibold mb-1">${call.toolName()} · ${call.providerId()}</h3>
                                <p class="has-text-grey is-size-7 mb-2">
                                    ${call.timestamp()} · ${call.durationLabel()}@if(call.sideEffecting()) · side-effecting@endif
                                </p>
                                @if(call.errorDetail() != null)
                                    <p class="notification is-danger is-light py-2 my-2" data-call-error>${call.errorDetail()}</p>
                                @endif
                                @if(call.resultPreview() != null && !call.resultPreview().isBlank())
                                    <p class="is-size-7"><strong>Result:</strong> ${call.resultPreview()}</p>
                                @endif
                            </div>
                            <span class="tag ${call.outcomeClass()}">${call.outcomeLabel()}</span>
                        </article>
                    @endfor
                </div>
            @endif
        </section>
    </div>
</main>
</body>
</html>
