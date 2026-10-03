@param boolean metricsEnabled
@param String activePage

<nav class="navbar navbar-expand-lg bg-body border-bottom" aria-label="Zalava control navigation">
    <div class="container-fluid px-4">
        <a class="navbar-brand fw-semibold" href="/zalava/control">Zalava Control</a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#zalava-navbar"
                aria-controls="zalava-navbar" aria-expanded="false" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="zalava-navbar">
            <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                @if("runtime".equals(activePage))
                    <li class="nav-item"><a class="nav-link active" aria-current="page" href="/zalava/control">Runtime</a></li>
                @else
                    <li class="nav-item"><a class="nav-link" href="/zalava/control">Runtime</a></li>
                @endif
                @if(metricsEnabled)
                    @if("metrics".equals(activePage))
                        <li class="nav-item"><a class="nav-link active" aria-current="page" href="/zalava/control/metrics">Metrics</a></li>
                    @else
                        <li class="nav-item"><a class="nav-link" href="/zalava/control/metrics">Metrics</a></li>
                    @endif
                @endif
                <li class="nav-item"><a class="nav-link" href="/chat">Chat</a></li>
                <li class="nav-item"><a class="nav-link" href="/api/zalava/providers">Providers JSON</a></li>
                <li class="nav-item"><a class="nav-link" href="/api/zalava/bootstrap-verification">Verification JSON</a></li>
            </ul>
            <span class="badge text-bg-success">dev/test</span>
        </div>
    </div>
</nav>
