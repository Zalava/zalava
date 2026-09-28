@import org.zalava.ui.SeaControlUiController.SeaControlUiModel

@param SeaControlUiModel model
@param boolean metricsEnabled

<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>SEA Control</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="/sea-control.css">
    <script src="https://unpkg.com/htmx.org@2.0.4" defer></script>
    <script>
        document.addEventListener("htmx:configRequest", function (event) {
            var token = document.cookie.match("(?:^|; )XSRF-TOKEN=([^;]*)");
            if (token) event.detail.headers["X-XSRF-TOKEN"] = decodeURIComponent(token[1]);
        });
    </script>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js" defer></script>
</head>
<body class="bg-body-tertiary">
    @template.sea.control.nav(metricsEnabled = metricsEnabled, activePage = "runtime")

  <main id="sea-control" class="container-fluid px-4 py-4">
        @template.sea.control.workspace(model = model)
    </main>
</body>
</html>
