@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.web.ui.ZalavaControlUiController.ZalavaControlUiModel

@param ZalavaControlUiModel model
@param boolean metricsEnabled
@param NavigationModel navigation

<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Zalava Advanced Settings</title>
    <link href="/css/vendor/bootstrap-5.3.8.min.css" rel="stylesheet">
    <link rel="stylesheet" href="/zalava-ui.css">
    <link rel="stylesheet" href="/zalava-control.css">
    <script src="/css/vendor/htmx-2.0.4.min.js" defer></script>
    <script>
        document.addEventListener("htmx:configRequest", function (event) {
            var token = document.cookie.match("(?:^|; )XSRF-TOKEN=([^;]*)");
            if (token) event.detail.headers["X-XSRF-TOKEN"] = decodeURIComponent(token[1]);
        });
    </script>
    <script src="/css/vendor/bootstrap-5.3.8.bundle.min.js" defer></script>
</head>
<body class="zalava-advanced">
    @template.ui.navigation(items = navigation.items(), activePage = "advanced", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken())

  <div class="advanced-destinations px-4 pt-4"><a href="/zalava/accounts">Manage accounts</a>@if(metricsEnabled)<a href="/zalava/control/metrics">Runtime metrics</a>@endif</div>
  <main id="zalava-control" class="container-fluid px-4 py-4">
        @template.zalava.control.workspace(model = model)
    </main>
</body>
</html>
