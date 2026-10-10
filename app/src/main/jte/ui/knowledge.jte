@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.web.ui.KnowledgeController.KnowledgeModel
@import org.zalava.knowledge.application.KnowledgeLibrary.SourceSummary
@param KnowledgeModel model
@param NavigationModel navigation
<!doctype html>
<html lang="en" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Knowledge</title>
  <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
  <link rel="stylesheet" href="/zalava-ui.css">
</head>
<body>
  @template.ui.navigation(items = navigation.items(), activePage = "knowledge", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken())
  <main class="section">
    <div class="container">
      <header class="mb-5">
        <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7">Library</p>
        <h1 class="title is-3">Knowledge</h1>
        <p class="has-text-grey">Your private and explicitly shared source metadata.</p>
      </header>
      <form method="post" action="/knowledge/upload" enctype="multipart/form-data" class="box">
        <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
        <div class="field">
          <label class="label" for="knowledge-file">Upload source</label>
          <div class="control"><input id="knowledge-file" type="file" name="file" required></div>
        </div>
        <button class="button is-primary">Upload</button>
      </form>
      <form method="get" action="/knowledge" class="field has-addons">
        <div class="control is-expanded">
          <input class="input" type="search" name="query" value="${model.query()}" maxlength="256" aria-label="Search knowledge">
          <input class="input mt-2" type="text" name="contentType" value="${model.contentType()}" maxlength="255" placeholder="Declared content type" aria-label="Filter by content type">
          <input class="input mt-2" type="text" name="processingState" value="${model.processingState()}" maxlength="32" placeholder="Processing state" aria-label="Filter by processing state">
        </div>
        <div class="control"><button class="button is-primary">Search</button></div>
      </form>
      @if(model.error() != null)<p class="notification is-danger is-light">${model.error()}</p>@endif
      @if(model.sources().isEmpty())
        <p class="has-text-grey">No sources.</p>
      @else
        <ul>
        @for(SourceSummary source : model.sources())
          <li class="box">
            <a class="has-text-weight-bold" href="/knowledge/${source.id().value().toString()}">${source.displayName()}</a><br>
            <span class="is-size-7 has-text-grey">${source.contentType()} · ${source.processingState()}</span>
            <form method="post" action="/knowledge/${source.id().value().toString()}/retry" class="mt-3">
              <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
              <button class="button is-small">Retry</button>
            </form>
            <form method="post" action="/knowledge/${source.id().value().toString()}/visibility" class="mt-3">
              <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
              <input type="hidden" name="shared" value="${source.visibility() == org.zalava.knowledge.domain.KnowledgeVisibility.PRIVATE}">
              <button class="button is-small">${source.visibility() == org.zalava.knowledge.domain.KnowledgeVisibility.PRIVATE ? "Share" : "Make private"}</button>
            </form>
            <form method="post" action="/knowledge/${source.id().value().toString()}/delete" class="mt-2">
              <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
              <input type="hidden" name="confirmed" value="true">
              <button class="button is-small is-danger is-light">Delete</button>
            </form>
          </li>
        @endfor
        </ul>
      @endif
    </div>
  </main>
</body>
</html>
