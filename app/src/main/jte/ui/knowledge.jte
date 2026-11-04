@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.web.ui.KnowledgeController.KnowledgeModel
@import org.zalava.knowledge.application.KnowledgeLibrary.SourceSummary
@import org.zalava.knowledge.domain.KnowledgeVisibility
@import org.zalava.knowledge.domain.SourceProcessingState
@import org.zalava.web.ui.LibraryPresentation
@param String knowledgeMessage = null
@param String knowledgeError = null
@param KnowledgeModel model
@param NavigationModel navigation
<!doctype html>
<html lang="en" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Knowledge</title>
  <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
  <link rel="stylesheet" href="/zalava-tokens.css">
<link rel="stylesheet" href="/zalava-ui.css">
<script src="/zalava-ui.js" defer></script>
</head>
<body>
  @template.ui.navigation(items = navigation.items(), activePage = "knowledge", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
  <main class="section">
    <div class="container">
      <header class="mb-5">
        <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7">Library</p>
        <h1 class="title is-3">Knowledge</h1>
        <p class="has-text-grey">Files you have uploaded or that others have shared with you.</p>
      </header>
      @if(knowledgeMessage != null)<p class="notification is-success is-light" role="status">${knowledgeMessage}</p>@endif
      @if(knowledgeError != null)<p class="notification is-danger is-light" role="alert">${knowledgeError}</p>@endif
      @if(!model.extractorAvailable())
        <aside class="notification is-warning is-light" aria-label="Document processing prerequisite"><p class="has-text-weight-bold">Document processing module required</p><p>Files can be stored now. Reading and searching their contents requires an active document processing module.</p>
        @if(model.administrator())<a href="/modules">Browse modules</a>@else<p>Ask your workspace administrator to install and activate a document processing module.</p>@endif
        </aside>
      @endif
      <form method="post" action="/knowledge/upload" enctype="multipart/form-data" class="box knowledge-upload">
        <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
        <div class="field">
          <label class="label" for="knowledge-file">Upload source</label>
          <div class="control"><input id="knowledge-file" type="file" name="file" accept=".txt,.md,.markdown,.html,.htm,.pdf,.docx,text/plain,text/html,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" required aria-describedby="knowledge-upload-help"></div>
          <p class="help" id="knowledge-upload-help">Upload plain text, HTML, PDF or a Word document. A processing module is needed to read the contents.</p>
        </div>
        <button class="button is-primary" data-file-submit>Upload</button><p class="help" data-file-hint role="status">Select a file to upload.</p>
      </form>
      <form method="get" action="/knowledge" class="library-search">
        <div class="field"><label class="label" for="knowledge-query">Search knowledge</label><input class="input" id="knowledge-query" type="search" name="query" value="${model.query()}" maxlength="256"></div>
        <div class="field"><label class="label" for="knowledge-type">File type</label><div class="select is-fullwidth"><select id="knowledge-type" name="contentType">
          <option value="" selected="${model.contentType().isEmpty()}">All file types</option>
          <option value="text/plain" selected="${model.contentType().equals("text/plain")}">Plain text</option>
          <option value="text/html" selected="${model.contentType().equals("text/html")}">HTML</option>
          <option value="application/pdf" selected="${model.contentType().equals("application/pdf")}">PDF</option>
          <option value="application/vnd.openxmlformats-officedocument.wordprocessingml.document" selected="${model.contentType().equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")}">Word document</option>
        </select></div></div>
        <div class="field"><label class="label" for="knowledge-state">Processing status</label><div class="select is-fullwidth"><select id="knowledge-state" name="processingState">
          <option value="" selected="${model.processingState().isEmpty()}">All statuses</option>
          @for(SourceProcessingState state : SourceProcessingState.values())<option value="${state.name()}" selected="${model.processingState().equals(state.name())}">${LibraryPresentation.state(state)}</option>@endfor
        </select></div></div>
        <div><button class="button is-primary">Search</button></div>
      </form>
      @if(model.error() != null)<p class="notification is-danger is-light" role="alert">${model.error()}</p>@endif
      @if(model.sources().isEmpty())
        <section class="empty-state" aria-label="Knowledge results">
          @if(!model.query().isBlank() || !model.contentType().isBlank() || !model.processingState().isBlank())
            <p class="has-text-weight-bold">No matching sources.</p><p>Try another search or remove the filters.</p><a class="button is-light" href="/knowledge">Clear filters</a>
          @else
            <p class="has-text-weight-bold">No sources yet.</p><p>Upload a file to keep it in your private Knowledge library.</p><a class="button is-light" href="#knowledge-file">Upload a source</a>
          @endif
        </section>
      @else
        <ul>
        @for(SourceSummary source : model.sources())
          <li class="box knowledge-row">
            <a class="has-text-weight-bold" href="/knowledge/${source.id().value().toString()}">${source.displayName()}</a>
            <span class="is-size-7 has-text-grey">${LibraryPresentation.fileType(source.contentType())} · ${LibraryPresentation.state(source.processingState())}</span>
            @if(source.owned())
            <div class="library-actions">
            <form method="post" action="/knowledge/${source.id().value().toString()}/retry" class="mt-3">
              <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
              <button class="button is-small">Retry</button>
            </form>
            <form method="post" action="/knowledge/${source.id().value().toString()}/visibility" class="mt-3">
              <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
              <input type="hidden" name="shared" value="${source.visibility() == KnowledgeVisibility.PRIVATE ? "true" : "false"}">
              <button class="button is-small">${source.visibility() == KnowledgeVisibility.PRIVATE ? "Share" : "Make private"}</button>
            </form>
            @template.ui.deleteConfirmation(action = "/knowledge/" + source.id().value().toString() + "/delete", item = source.displayName(), csrfName = model.csrf().getParameterName(), csrfValue = model.csrf().getToken(), consequence = "The stored source and its processed content will be removed.")
            </div>
            @else<p class="tag is-light mt-3">Shared read-only</p>@endif
          </li>
        @endfor
        </ul>
      @endif
    </div>
  </main>
</body>
</html>
