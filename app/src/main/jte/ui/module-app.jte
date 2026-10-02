@param String title
@param String content
@param org.zalava.web.ui.navigation.NavigationModel navigation

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title}</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
    <link rel="stylesheet" href="/sea-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "apps")

<main class="section">
    <div class="container">$unsafe{content}</div>
</main>

</body>
</html>
