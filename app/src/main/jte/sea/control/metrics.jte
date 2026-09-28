@param boolean metricsEnabled

<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>SEA metrics</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="/sea-control.css">
</head>
<body class="bg-body-tertiary">
    @template.sea.control.nav(metricsEnabled = metricsEnabled, activePage = "metrics")

    <main class="container-fluid px-4 py-4">
        <h1 class="h4">Metrics viewer</h1>
        <div class="alert alert-warning" role="alert">
            <p class="mb-2 fw-semibold">No metrics viewer is configured.</p>
            <p class="mb-0">
                Enable the managed Prometheus/Grafana stack with
                <code>scripts/sea-workflow observability enable managed</code>, or point SEA at an
                operator-owned viewer with
                <code>scripts/sea-workflow observability enable external --grafana-url https://…</code>,
                then restart SEA so the new mode is applied.
            </p>
        </div>
        <a class="btn btn-primary" href="/sea/control">Return to SEA Control</a>
    </main>
</body>
</html>
