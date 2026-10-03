@import org.zalava.web.ui.JobsController.JobGroup
@import org.zalava.web.ui.JobsController.JobsModel

@param JobsModel model
@param org.zalava.web.ui.navigation.NavigationModel navigation
@param org.zalava.tasks.application.port.in.ActorJobEvidenceQueries.Snapshot evidence

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Jobs</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
    <link rel="stylesheet" href="/zalava-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "jobs")

<main class="section">
    <div class="container">
        <header class="jobs-heading mb-6">
            <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-2">Default workspace</p>
            <h1 class="title is-2 mb-3">Jobs</h1>
            <p class="subtitle is-5 has-text-grey">
                Persisted work grouped by its current Zalava task status.
            </p>
        </header>

        @if(model.totalJobs() == 0)
            <section class="box jobs-empty has-text-centered py-6">
                <h2 class="title is-4 mb-2">No jobs have been created yet.</h2>
                <p class="has-text-grey">Start in Chat to create work for Zalava.</p>
                <a class="button is-primary is-light mt-4" href="/chat">Open Chat</a>
            </section>
        @else
            <div class="jobs-groups">
                @for(JobGroup group : model.groups())
                    <section class="box jobs-group" data-group="${group.key()}" data-count="${group.jobs().size()}">
                        <div class="level is-mobile mb-4">
                            <div class="level-left">
                                <h2 class="title is-4 mb-0">${group.title()}</h2>
                            </div>
                            <div class="level-right">
                                <span class="tag is-rounded ${group.statusClass()}">${group.jobs().size()}</span>
                            </div>
                        </div>

                        @if(group.jobs().isEmpty())
                            <p class="has-text-grey is-size-7">No ${group.title().toLowerCase()} jobs.</p>
                        @else
                            <div class="job-list">
                                @for(var job : group.jobs())
                                    <article class="job-entry">
                                        <div class="job-copy">
                                            <h3 class="has-text-weight-semibold mb-1">
                                                <a class="job-link" href="${job.url()}">${job.name()}</a>
                                            </h3>
                                            <p class="has-text-grey is-size-7 mb-2">Created ${job.createdAt()}</p>
                                            @if(job.description() != null && !job.description().isBlank())
                                                <p class="job-description">${job.description()}</p>
                                            @endif
                                        </div>
                                        <span class="tag ${group.statusClass()}">${group.title()}</span>
                                    </article>
                                @endfor
                            </div>
                        @endif
                    </section>
                @endfor
            </div>
        @endif
    </div>
@template.ui.jobEvidence(evidence = evidence)
</main>
</body>
</html>
