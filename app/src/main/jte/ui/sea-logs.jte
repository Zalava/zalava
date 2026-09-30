@param org.zalava.ui.SeaApplicationLogs.Snapshot logs
@param org.zalava.ui.navigation.NavigationModel navigation
<!doctype html>
<html lang="en" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Logs</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
  <link rel="stylesheet" href="/sea-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "monitoring")
<main class="section">
  <div class="container">
    <a href="/monitoring">← Monitoring</a>
        <h1 class="title is-2 mt-4">Zalava logs</h1>
    <p class="subtitle is-5">Recent application output, including earlier runs when retained by log rotation.</p>
    <form method="get" action="/monitoring/logs" class="mb-4">
      <button class="button is-link" type="submit">Refresh logs</button>
    </form>
    @if(!logs.available())
      <div class="notification is-warning" role="status">${logs.message()}</div>
    @else
      @if(logs.lines().isEmpty())
        <div class="notification is-info" role="status">No log lines have been recorded yet.</div>
      @else
        <section class="box" aria-label="Recent SEA log lines">
          <pre class="sea-log-output">
          @for(String line : logs.lines())
${line}
          @endfor
          </pre>
        </section>
      @endif
    @endif
  </div>
</main>
</body>
</html>
