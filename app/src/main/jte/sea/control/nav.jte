@param boolean metricsEnabled
@param String activePage

<nav class="navbar navbar-expand-lg bg-body border-bottom" aria-label="SEA control navigation">
    <div class="container-fluid px-4">
        <a class="navbar-brand fw-semibold" href="/sea/control">SEA Control</a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#sea-navbar"
                aria-controls="sea-navbar" aria-expanded="false" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="sea-navbar">
            <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                @if("runtime".equals(activePage))
                    <li class="nav-item"><a class="nav-link active" aria-current="page" href="/sea/control">Runtime</a></li>
                @else
                    <li class="nav-item"><a class="nav-link" href="/sea/control">Runtime</a></li>
                @endif
                @if(metricsEnabled)
                    @if("metrics".equals(activePage))
                        <li class="nav-item"><a class="nav-link active" aria-current="page" href="/sea/control/metrics">Metrics</a></li>
                    @else
                        <li class="nav-item"><a class="nav-link" href="/sea/control/metrics">Metrics</a></li>
                    @endif
                @endif
                <li class="nav-item"><a class="nav-link" href="/chat">Chat</a></li>
                <li class="nav-item"><a class="nav-link" href="/api/sea/providers">Providers JSON</a></li>
                <li class="nav-item"><a class="nav-link" href="/api/sea/bootstrap-verification">Verification JSON</a></li>
            </ul>
            <span class="badge text-bg-success">dev/test</span>
        </div>
    </div>
</nav>
