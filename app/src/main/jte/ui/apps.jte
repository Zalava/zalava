@import java.util.List
@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.modules.web.application.RegisteredWebPage
@param List<RegisteredWebPage> pages
@param NavigationModel navigation

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Apps</title>
    <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
    <link rel="stylesheet" href="/zalava-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "apps", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken())

<main class="section">
    <div class="container">
        <header class="dashboard-heading mb-6">
            <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-2">Module apps</p>
            <h1 class="title is-2 mb-3">Apps</h1>
            <p class="subtitle is-5 has-text-grey">Screens contributed by installed Zalava modules.</p>
        </header>
        @if(pages.isEmpty())
            <section class="box dashboard-activity">
                <p class="has-text-weight-semibold mb-1">No module apps are installed.</p>
                <p class="has-text-grey">Installed modules can contribute screens here when they expose web extensions.</p>
            </section>
        @else
            <section class="box dashboard-activity">
                <div class="activity-list">
                    @for(RegisteredWebPage page : pages)
                        <a class="activity-entry" href="${page.path()}">
                            <div class="activity-copy">
                                <h2 class="has-text-weight-semibold">${page.title()}</h2>
                                <p class="has-text-grey is-size-7">${page.moduleDisplayName()} - ${page.description()}</p>
                            </div>
                            <span class="tag is-light">${page.navSection()}</span>
                        </a>
                    @endfor
                </div>
            </section>
        @endif
    </div>
</main>

</body>
</html>
