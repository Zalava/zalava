@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.knowledge.application.KnowledgeLibrary.SourceDetail
@import org.zalava.knowledge.application.KnowledgeProcessingQueries.Status
@import org.zalava.web.ui.LibraryPresentation
@param Status processing
@param boolean administrator
@param SourceDetail model
@param NavigationModel navigation
<!doctype html>
<html lang="en" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Knowledge Source</title>
  <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
  <link rel="stylesheet" href="/zalava-ui.css">
</head>
<body>
  @template.ui.navigation(items = navigation.items(), activePage = "knowledge", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
  <main class="section">
    <div class="container">
      <a href="/knowledge" class="is-size-7">Back to knowledge</a>
      <header class="my-5">
        <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7">Source provenance</p>
        <h1 class="title is-3">${model.displayName()}</h1>
      </header>
      <section class="box" aria-labelledby="processing-heading"><h2 class="title is-5" id="processing-heading">Processing status</h2><p>${LibraryPresentation.processing(processing)}</p>
        @if(!processing.extractorAvailable())<p class="mt-3">Document processing module required</p>@if(administrator)<a href="/modules">Browse modules</a>@else<p>Ask your workspace administrator to install and activate a document processing module.</p>@endif@endif
        @if(model.owned())<a class="button is-light mt-3" href="/knowledge">Retry or manage this source</a>@else<p class="tag is-light mt-3">Shared read-only</p>@endif
      </section>
      <details class="box"><summary>Source details</summary>
      <dl class="content">
        <dt>Source ID</dt><dd>${model.id().value().toString()}</dd>
        <dt>Declared type</dt><dd>${model.contentType()}</dd>
        <dt>Size</dt><dd>${model.byteCount()} bytes</dd>
        <dt>Visibility</dt><dd>${model.visibility()}</dd>
        <dt>Processing state</dt><dd>${model.processingState()}</dd>
        <dt>Created</dt><dd><time datetime="${model.createdAt().toString()}">${LibraryPresentation.date(model.createdAt())}</time></dd>
        <dt>Last updated</dt><dd><time datetime="${model.updatedAt().toString()}">${LibraryPresentation.date(model.updatedAt())}</time></dd>
        <dt>Source version</dt><dd>${model.version()}</dd>
      </dl></details>
    </div>
  </main>
</body>
</html>
