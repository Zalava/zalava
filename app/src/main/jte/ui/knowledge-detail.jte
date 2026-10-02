@import org.zalava.knowledge.application.KnowledgeLibrary.SourceDetail
@param SourceDetail model
@param org.zalava.web.ui.navigation.NavigationModel navigation
<!doctype html>
<html lang="en" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Knowledge Source</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
  <link rel="stylesheet" href="/sea-ui.css">
</head>
<body>
  @template.ui.navigation(items = navigation.items(), activePage = "knowledge")
  <main class="section">
    <div class="container">
      <a href="/knowledge" class="is-size-7">Back to knowledge</a>
      <header class="my-5">
        <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7">Source provenance</p>
        <h1 class="title is-3">${model.displayName()}</h1>
      </header>
      <dl class="content">
        <dt>Source ID</dt><dd>${model.id().value().toString()}</dd>
        <dt>Declared type</dt><dd>${model.contentType()}</dd>
        <dt>Size</dt><dd>${model.byteCount()} bytes</dd>
        <dt>Visibility</dt><dd>${model.visibility()}</dd>
        <dt>Processing state</dt><dd>${model.processingState()}</dd>
        <dt>Created</dt><dd>${model.createdAt().toString()}</dd>
        <dt>Last updated</dt><dd>${model.updatedAt().toString()}</dd>
        <dt>Source version</dt><dd>${model.version()}</dd>
      </dl>
    </div>
  </main>
</body>
</html>
