@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.web.ui.JobDetailController.JobDetailModel
@import org.zalava.web.ui.JobDetailController.ApprovalEntry
@import org.zalava.web.ui.JobDetailController.LifecycleEntry
@import org.zalava.web.ui.JobDetailController.SectionEntry
@import org.springframework.security.web.csrf.CsrfToken

@param JobDetailModel model
@param NavigationModel navigation
@param CsrfToken csrf

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${model.name()} - Zalava Job</title>
    <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
    <link rel="stylesheet" href="/zalava-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "jobs")

<main class="section">
    <div class="container">
        <nav class="breadcrumb mb-5" aria-label="breadcrumbs">
            <ul>
                <li><a href="/jobs">Jobs</a></li>
                <li class="is-active"><a href="#" aria-current="page">${model.name()}</a></li>
            </ul>
        </nav>

        <header class="job-detail-heading mb-6">
            <div class="job-detail-title">
                <div>
                    <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-2">
                        Default workspace
                    </p>
                    <h1 class="title is-2 mb-3">${model.name()}</h1>
                    <p class="subtitle is-6 has-text-grey">Created ${model.createdAt()}</p>
                </div>
                <span class="tag is-medium ${model.statusClass()}">${model.statusLabel()}</span>
            </div>
        </header>

        <nav class="tabs is-boxed job-detail-tabs mb-5" aria-label="Job detail sections">
            <ul>
                <li class="is-active"><a href="#overview">Overview</a></li>
                <li><a href="#result">Result</a></li>
                <li><a href="#plan">Plan</a></li>
                <li><a href="#artifacts">Artifacts</a></li>
                <li><a href="#logs">Logs</a></li>
            </ul>
        </nav>

        <div class="columns is-variable is-5">
            <div class="column is-two-thirds">
                <section id="overview" class="box job-detail-card mb-5">
                    <p class="heading mb-2">Overview</p>
                    <h2 class="title is-4">Goal and context</h2>
                    @if(model.goalDescription() == null || model.goalDescription().isBlank())
                        <p class="has-text-grey">No description was recorded for this job.</p>
                    @else
                        <div class="job-detail-description">${model.goalDescription()}</div>
                    @endif
                    <div class="notification is-info is-light mt-5">
                        <p class="heading mb-2">Current step</p>
                        <p class="has-text-weight-semibold mb-1">${model.currentStepLabel()}</p>
                        <p>${model.currentStepDetail()}</p>
                    </div>
                </section>

                <section class="box job-detail-card mb-5">
                    <p class="heading mb-2">Lifecycle</p>
                    <h2 class="title is-4">What has happened</h2>
                    <ol class="job-lifecycle">
                        @for(LifecycleEntry entry : model.lifecycle())
                            <li class="job-lifecycle-entry">
                                <span class="job-lifecycle-marker" aria-hidden="true"></span>
                                <div>
                                    <div class="job-lifecycle-heading">
                                        <p class="has-text-weight-semibold">${entry.label()}</p>
                                        @if(entry.occurredAt() != null)
                                            <time class="has-text-grey is-size-7">${entry.occurredAt()}</time>
                                        @endif
                                    </div>
                                    <p class="has-text-grey">${entry.detail()}</p>
                                </div>
                            </li>
                        @endfor
                    </ol>
                </section>

                <section id="result" class="box job-detail-card mb-5">
                    <p class="heading mb-2">Execution result</p>
                    <h2 class="title is-4">Agent feedback</h2>
                    @if(model.failureDetail() != null && !model.failureDetail().isBlank())
                        <article class="notification is-danger is-light mb-5">
                            <p class="has-text-weight-semibold mb-2">Failure detail</p>
                            <p class="job-failure-detail">${model.failureDetail()}</p>
                        </article>
                    @endif
                    @if(model.agentFeedback() == null || model.agentFeedback().isBlank())
                        <div class="notification is-light">
                            Zalava has not recorded an execution result for this job yet.
                        </div>
                    @else
                        <div class="job-detail-result">${model.agentFeedback()}</div>

                    @endif
                </section>

                <section id="approvals" class="box job-detail-card mb-5">
                    <p class="heading mb-2">Approvals</p>
                    <h2 class="title is-4">Actions requiring permission</h2>
                    @if(model.approvals().isEmpty())
                        <div class="notification is-light">
                            No approval is currently required for this job.
                        </div>
                    @else
                        <div class="approval-list">
                            @for(ApprovalEntry approval : model.approvals())
                                <article class="notification is-warning is-light approval-card">
                                    <div class="approval-heading">
                                        <div>
                                            <p class="has-text-weight-semibold">${approval.toolName()}</p>
                                            <p class="is-size-7 has-text-grey">${approval.providerId()}</p>
                                        </div>
                                        <span class="tag ${approval.pending() ? "is-warning" : approval.decision().equals("Denied") ? "is-danger" : "is-success"}">${approval.decision()}</span>
                                    </div>
                                    <pre class="approval-arguments mt-4"><code>${approval.argumentsJson()}</code></pre>
                                    @if(!approval.policyTags().isEmpty())
                                        <div class="tags mt-3">
                                            @for(String tag : approval.policyTags())
                                                <span class="tag is-light">${tag}</span>
                                            @endfor
                                        </div>
                                    @endif
                                    @if(approval.pending())
                                    <div class="buttons mt-4">
                                        <form method="post"
                                              action="/jobs/${model.referencePath()}/approvals/${approval.requestId()}/allow">
                        <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                                            <button class="button is-success" type="submit">Allow and continue</button>
                                        </form>
                                        <form method="post"
                                              action="/jobs/${model.referencePath()}/approvals/${approval.requestId()}/allow-tool">
                        <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                                            <button class="button is-link is-light" type="submit">Always allow tool</button>
                                        </form>
                                        <form method="post"
                                              action="/jobs/${model.referencePath()}/approvals/${approval.requestId()}/deny">
                        <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                                            <button class="button is-danger is-light" type="submit">Deny and continue</button>
                                        </form>
                                    </div>
                                    @endif
                                </article>
                            @endfor
                        </div>
                    @endif
                </section>

                <section id="plan" class="box job-detail-card mb-5">
                    <p class="heading mb-2">Plan</p>
                    <h2 class="title is-4">Planned work</h2>
                    @if(model.planEntries().isEmpty())
                        <div class="notification is-light">
                            No structured plan is recorded for this job yet.
                        </div>
                    @else
                        <div class="content">
                            <ul>
                                @for(SectionEntry entry : model.planEntries())
                                    <li>
                                        <strong>${entry.label()}</strong>
                                        <p>${entry.detail()}</p>
                                    </li>
                                @endfor
                            </ul>
                        </div>
                    @endif
                </section>

                <section id="artifacts" class="box job-detail-card mb-5">
                    <p class="heading mb-2">Artifacts</p>
                    <h2 class="title is-4">Produced files and results</h2>
                @if(model.agentFeedback() != null && !model.agentFeedback().isBlank() && !model.referencePath().contains("/"))
                    <p>Saved job report</p>
                    <a class="button is-link is-light mb-3" href="/jobs/${model.referencePath()}/artifacts/report">Download saved report</a>
                @endif
                    @if(model.artifactEntries().isEmpty())
                        <div class="notification is-light">
                            @if(model.agentFeedback() != null && !model.agentFeedback().isBlank() && !model.referencePath().contains("/"))
                        No additional artifacts have been linked to this job yet.
                    @else
                        No artifacts are linked to this job yet.
                    @endif
                        </div>
                    @else
                        <div class="content">
                            <ul>
                                @for(SectionEntry entry : model.artifactEntries())
                                    <li>
                                        <strong>${entry.label()}</strong>
                                        <p>${entry.detail()}</p>
                                    </li>
                                @endfor
                            </ul>
                        </div>
                    @endif
                </section>

                <section id="logs" class="box job-detail-card">
                    <p class="heading mb-2">Logs</p>
                    <h2 class="title is-4">Structured execution events</h2>
                    @if(model.logEntries().isEmpty())
                        <div class="notification is-light">
                            No structured logs are recorded for this job yet.
                        </div>
                    @else
                        <div class="content">
                            <ul>
                                @for(SectionEntry entry : model.logEntries())
                                    <li>
                                        <strong>${entry.label()}</strong>
                                        <p>${entry.detail()}</p>
                                    </li>
                                @endfor
                            </ul>
                        </div>
                    @endif
                </section>
            </div>

            <div class="column">
                <aside class="box job-detail-card job-detail-summary">
                    <p class="heading mb-3">Job status</p>
                    <span class="tag is-medium ${model.statusClass()}">${model.statusLabel()}</span>
                    <p class="mt-4">${model.statusGuidance()}</p>
                    <hr>
                    <a class="button is-light is-fullwidth" href="/jobs">Back to Jobs</a>
                </aside>
            </div>
        </div>
    </div>
</main>
</body>
</html>
