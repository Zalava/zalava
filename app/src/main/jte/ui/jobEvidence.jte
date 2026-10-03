@param org.zalava.tasks.application.port.in.ActorJobEvidenceQueries.Snapshot evidence
<div class="container job-evidence" aria-label="Saved reports and upcoming work">
  <section class="jobs-group" aria-labelledby="saved-reports-title">
    <h2 id="saved-reports-title" class="title is-4">Saved job reports</h2>
    <p>Saved job feedback, ordered by job creation.</p>
    @if(evidence.reports().isEmpty())
      <p>No saved job reports.</p>
    @else
      <ul class="job-list">
      @for(var report : evidence.reports())
        <li class="job-entry">
          <div class="job-copy">
            <h3><a href="/jobs/${report.reference().value()}">${report.name()}</a></h3>
            <p>Job created <time datetime="${report.createdAt().toString()}">${report.createdAt().toString()}</time> · ${report.status().name().replace('_', ' ')}</p>
            <a class="button is-link is-light" href="/jobs/${report.reference().value()}/artifacts/report">Download saved report</a>
          </div>
        </li>
      @endfor
      </ul>
    @endif
  </section>
  <section class="jobs-group" aria-labelledby="upcoming-jobs-title">
    <h2 id="upcoming-jobs-title" class="title is-4">Upcoming scheduled jobs</h2>
    <p>Due times come from the scheduler's saved records.</p>
    @if(evidence.schedulingUnavailable())
      <p role="alert">Scheduled work is unavailable. Reload to try again.</p>
    @elseif(evidence.upcoming().isEmpty())
      <p>No upcoming scheduled jobs.</p>
    @else
      <ul class="job-list">
      @for(var job : evidence.upcoming())
        <li class="job-entry"><div class="job-copy">
          <h3><a href="/jobs/${job.reference().value()}">${job.name()}</a></h3>
          <p>Scheduled for <time datetime="${job.scheduledAt().toString()}">${job.scheduledAt().toString()}</time></p>
        </div></li>
      @endfor
      </ul>
    @endif
  </section>
</div>
