@param boolean metricsEnabled

<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Zalava metrics</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="/zalava-control.css">
</head>
<body class="bg-body-tertiary">
    @template.zalava.control.nav(metricsEnabled = metricsEnabled, activePage = "metrics")

    <main class="container-fluid px-4 py-4">
        <h1 class="h4">Metrics viewer</h1>
        <div class="alert alert-warning" role="alert">
            <p class="mb-2 fw-semibold">No metrics viewer is configured.</p>
            <p class="mb-0">
                Enable managed observability for your installation or configure an external Grafana viewer,
                then restart Zalava to apply the new mode.
            </p>
        </div>
        <a class="btn btn-primary" href="/zalava/control">Return to Zalava Control</a>
    </main>
</body>
</html>
