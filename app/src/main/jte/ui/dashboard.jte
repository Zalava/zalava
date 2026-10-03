@import org.zalava.web.ui.DashboardController.ActivityEntry
@import org.zalava.web.ui.DashboardController.ApprovalEntry
@import org.zalava.web.ui.DashboardController.CurrentWorkEntry
@import org.zalava.web.ui.DashboardController.DashboardModel

@param DashboardModel model
@param org.zalava.web.ui.navigation.NavigationModel navigation
@param org.zalava.tasks.application.port.in.ActorJobEvidenceQueries.Snapshot evidence

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Dashboard</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
    <link rel="stylesheet" href="/sea-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "dashboard")

<main class="section">
    <div class="container">
        <header class="dashboard-heading mb-6">
            <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-2">Default workspace</p>
            <h1 class="title is-2 mb-3">What needs your attention?</h1>
            <p class="subtitle is-5 has-text-grey">
                Current work, approvals, and recent activity from Zalava.
            </p>
        </header>

        <section class="columns is-variable is-4 mb-5" aria-label="Workspace summary">
            <div class="column">
                <article class="box dashboard-metric">
                    <p class="heading">Running Jobs</p>
                    <p class="title is-2" data-metric="running-jobs">${model.runningJobs()}</p>
                    <p class="has-text-grey is-size-7">Work currently in progress</p>
                </article>
            </div>
            <div class="column">
                <article class="box dashboard-metric">
                    <p class="heading">Pending Approvals</p>
                    <p class="title is-2" data-metric="pending-approvals">${model.pendingApprovals()}</p>
                    <p class="has-text-grey is-size-7">Tasks or tool calls waiting for you</p>
                </article>
            </div>
            <div class="column">
                <article class="box dashboard-metric">
                    <p class="heading">Completed Today</p>
                    <p class="title is-2" data-metric="completed-today">${model.completedToday()}</p>
                    <p class="has-text-grey is-size-7">Tasks completed since midnight</p>
                </article>
            </div>
        </section>

        @if(!model.approvals().isEmpty())
            <section class="box dashboard-activity mb-5">
                <div class="level mb-4">
                    <div class="level-left">
                        <div>
                            <p class="heading mb-1">Approvals</p>
                            <h2 class="title is-4 mb-0">Waiting for your decision</h2>
                        </div>
                    </div>
                </div>
                <div class="activity-list">
                    @for(ApprovalEntry approval : model.approvals())
                        <a class="activity-entry" href="${approval.jobUrl()}">
                            <div class="activity-copy">
                                <h3 class="has-text-weight-semibold">${approval.toolName()}</h3>
                                <p class="has-text-grey is-size-7">${approval.providerId()}</p>
                            </div>
                            <span class="tag is-warning is-light">Review</span>
                        </a>
                    @endfor
                </div>
            </section>
        @endif

        <section class="box dashboard-activity mb-5">
            <div class="level mb-4">
                <div class="level-left">
                    <div>
                        <p class="heading mb-1">Current work</p>
                        <h2 class="title is-4 mb-0">Active jobs</h2>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-rounded">${model.currentWork().size()} active</span>
                </div>
            </div>
            @if(model.currentWork().isEmpty())
                <div class="notification is-light has-text-centered py-5">
                    <p class="has-text-weight-semibold mb-1">No active jobs right now.</p>
                    <p class="has-text-grey">Running and waiting jobs will appear here.</p>
                </div>
            @else
                <div class="activity-list">
                    @for(CurrentWorkEntry work : model.currentWork())
                        <a class="activity-entry" href="${work.url()}">
                            <div class="activity-copy">
                                <h3 class="has-text-weight-semibold">${work.name()}</h3>
                                <p class="has-text-grey is-size-7">${work.detail()}</p>
                            </div>
                            <span class="tag ${work.statusClass()}">${work.statusLabel()}</span>
                        </a>
                    @endfor
                </div>
            @endif
        </section>

        <section class="box dashboard-activity">
            <div class="level mb-4">
                <div class="level-left">
                    <div>
                        <p class="heading mb-1">Workspace</p>
                        <h2 class="title is-4 mb-0">Recent Activity</h2>
                    </div>
                </div>
                <div class="level-right">
                    <span class="tag is-rounded">${model.recentActivity().size()} today</span>
                </div>
            </div>

            @if(model.recentActivity().isEmpty())
                <div class="notification is-light has-text-centered py-6">
                    <p class="has-text-weight-semibold mb-1">No task activity today.</p>
                    <p class="has-text-grey">Start in Chat when you are ready to give Zalava something to do.</p>
                    <a class="button is-primary is-light mt-4" href="/chat">Open Chat</a>
                </div>
            @else
                <div class="activity-list">
                    @for(ActivityEntry activity : model.recentActivity())
                        <article class="activity-entry">
                            <div class="activity-copy">
                                <h3 class="has-text-weight-semibold">${activity.name()}</h3>
                                <p class="has-text-grey is-size-7">${activity.createdAt()}</p>
                            </div>
                            <span class="tag ${activity.statusClass()}">${activity.statusLabel()}</span>
                        </article>
                    @endfor
                </div>
            @endif
        </section>
    </div>
@template.ui.jobEvidence(evidence = evidence)
</main>

</body>
</html>
