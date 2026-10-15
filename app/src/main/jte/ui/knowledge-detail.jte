@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.knowledge.application.KnowledgeLibrary.SourceDetail
@import org.zalava.knowledge.application.KnowledgeProcessingQueries.Status
@import org.zalava.web.ui.LibraryPresentation
@param Status processing
@param boolean administrator
@param SourceDetail model
@param NavigationModel navigation
@param String knowledgeMessage = null
@param String knowledgeError = null
<!doctype html>
<html lang="en" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Knowledge Source</title>
  <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
  <link rel="stylesheet" href="/zalava-tokens.css">
<link rel="stylesheet" href="/zalava-ui.css">
<script src="/zalava-ui.js" defer></script>
</head>
<body>
  @template.ui.navigation(items = navigation.items(), activePage = "knowledge", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
  <main class="section">
    <div class="container source-layout">
      <a href="/knowledge">Back to Knowledge</a>
      <header class="my-5">
        <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7">Knowledge source</p>
        <h1 class="title is-3">${model.displayName()}</h1>
        <p class="has-text-grey">${model.owned() ? "Your source" : "Shared with you · read-only"}</p>
      </header>
      @if(knowledgeMessage != null)<p role="status" class="notification is-success is-light">${knowledgeMessage}</p>@endif
      @if(knowledgeError != null)<p role="alert" class="notification is-danger is-light" tabindex="-1" data-error-summary>${knowledgeError}</p>@endif
      <section class="box" aria-label="File information">
        <dl class="source-metadata">
          <div><dt>File type</dt><dd>${LibraryPresentation.fileType(model.contentType())}</dd></div>
          <div><dt>File size</dt><dd>${LibraryPresentation.fileSize(model.byteCount())}</dd></div>
          <div><dt>Visibility</dt><dd>${LibraryPresentation.visibility(model.visibility())}</dd></div>
          <div><dt>Added</dt><dd><time datetime="${model.createdAt().toString()}">${LibraryPresentation.date(model.createdAt())}</time></dd></div>
        </dl>
      </section>
      <section class="box" aria-labelledby="processing-heading">
        <h2 class="title is-5" id="processing-heading">Processing</h2>
        <p class="has-text-weight-semibold">${LibraryPresentation.state(processing.state())}</p>
        <p class="mt-3">${LibraryPresentation.processing(processing)}</p>
        @if(!processing.extractorAvailable())
          <p class="mt-3">The file is stored safely. Install and start a document-processing module to read its contents.</p>
          @if(administrator)<a class="button is-light mt-3" href="/modules">Find a processing module</a>@else<p class="mt-3">Ask your workspace administrator to set up document processing.</p>@endif
        @endif
      </section>
      @if(model.owned())
        <section class="box" aria-labelledby="source-actions-heading" data-source-actions>
          <h2 class="title is-5" id="source-actions-heading">Source actions</h2>
          <div class="source-actions">
            @if(processing.extractorAvailable())
            <form method="post" action="/knowledge/${model.id().value().toString()}/retry">
              <input type="hidden" name="${navigation.csrfParameter()}" value="${navigation.csrfToken()}">
              <button class="button is-light" type="submit">Retry processing</button>
            </form>
            @endif
            <form method="post" action="/knowledge/${model.id().value().toString()}/visibility">
              <input type="hidden" name="${navigation.csrfParameter()}" value="${navigation.csrfToken()}">
              <input type="hidden" name="shared" value="${model.visibility().name().equals("PRIVATE") ? "true" : "false"}">
              <button class="button is-light" type="submit">${model.visibility().name().equals("PRIVATE") ? "Share with workspace" : "Make private"}</button>
            </form>
            @template.ui.deleteConfirmation(action = "/knowledge/" + model.id().value().toString() + "/delete", item = model.displayName(), csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), consequence = "The stored source and its processed content will be removed.")
          </div>
        </section>
      @endif
      <details class="box source-technical"><summary>Technical details</summary>
        <dl class="source-metadata">
          <div><dt>Source ID</dt><dd>${model.id().value().toString()}</dd></div>
          <div><dt>Declared type</dt><dd>${model.contentType()}</dd></div>
          <div><dt>Source version</dt><dd>${model.version()}</dd></div>
          <div><dt>Last updated</dt><dd><time datetime="${model.updatedAt().toString()}">${LibraryPresentation.date(model.updatedAt())}</time></dd></div>
        </dl>
      </details>
    </div>
  </main>
</body>
</html>
