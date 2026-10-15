@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.web.ui.MemoryController.MemoryDetailModel
@import org.zalava.knowledge.memory.domain.MemoryScope
@import org.zalava.web.ui.LibraryPresentation
@param MemoryDetailModel model
@param NavigationModel navigation
@param String memoryMessage
@param String memoryError
<!doctype html>
<html lang="en" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Memory</title>
  <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
  <link rel="stylesheet" href="/zalava-tokens.css">
<link rel="stylesheet" href="/zalava-ui.css">
<script src="/zalava-ui.js" defer></script>
</head>
<body>
  @template.ui.navigation(items = navigation.items(), activePage = "memory", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
  <main class="section">
    <div class="container">
      <p class="mb-4"><a href="/memory">Back to memory</a></p>
      @if(memoryMessage != null)
        <article class="message is-success" role="status"><div class="message-body">${memoryMessage}</div></article>
      @endif
      @if(model.error() != null)
        <article class="message is-danger" role="alert" tabindex="-1" data-error-summary><div class="message-body">${model.error()}</div></article>
      @endif
      <h1 class="title is-3">Memory entry</h1><p class="has-text-grey mb-4">Private to your account. ${LibraryPresentation.scope(model.memory().scope())} is a category for recall, not a sharing setting.</p>
      <div class="box" data-memory-detail="${model.memory().id()}">
        <dl>
          <dt class="has-text-weight-bold">Text</dt><dd data-memory-text>${model.memory().text()}</dd>
          <dt class="has-text-weight-bold">Scope</dt><dd data-memory-scope>${LibraryPresentation.scope(model.memory().scope())}</dd>
          <dt class="has-text-weight-bold">Created</dt><dd><time datetime="${model.memory().createdAt().toString()}">${LibraryPresentation.date(model.memory().createdAt())}</time></dd>
          @if(model.memory().updatedAt() != null)
            <dt class="has-text-weight-bold">Last revised</dt><dd data-memory-updated><time datetime="${model.memory().updatedAt().toString()}">${LibraryPresentation.date(model.memory().updatedAt())}</time></dd>
          @endif
          <dt class="has-text-weight-bold">Provenance</dt><dd data-memory-provenance>${LibraryPresentation.provenance(model.memory().provenance().source())}@if(model.memory().provenance().reference() != null) · ${model.memory().provenance().reference()}@endif</dd>
        </dl>
        <details class="mt-3"><summary>Stored metadata</summary><dl class="content mt-3"><dt>Id</dt><dd data-memory-id>${model.memory().id()}</dd><dt>Stored scope</dt><dd>${model.memory().scope()}</dd><dt>Source</dt><dd>${model.memory().provenance().source()}</dd>
        @for(var entry : model.memory().metadata().entrySet())<dt>${entry.getKey()}</dt><dd>${entry.getValue()}</dd>@endfor
        </dl></details>
      </div>
      <form method="post" action="/memory/${model.memory().id()}/edit" class="box">
        <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
        <h2 class="title is-5">Edit memory</h2>
        <div class="field">
          <label class="label" for="memory-scope">Scope</label>
          <div class="control">
            <div class="select">
              <select id="memory-scope" name="scope">
                @for(MemoryScope option : LibraryPresentation.durableScopes())
                  @if(option.name().equals(model.scope()))
                    <option value="${option.name()}" selected>${LibraryPresentation.scope(option)}</option>
                  @else
                    <option value="${option.name()}">${LibraryPresentation.scope(option)}</option>
                  @endif
                @endfor
              </select>
            </div>
          </div>
        </div>
        <div class="field">
          <label class="label" for="memory-text">Memory <span class="field-requirement">(required)</span></label>
          <div class="control"><textarea class="textarea" id="memory-text" name="text" maxlength="2000" required aria-invalid="${model.error() != null ? "true" : null}" aria-describedby="${model.error() != null ? "memory-edit-error" : null}">${model.text()}</textarea></div>
        </div>
        @if(model.error() != null)<p class="field-error" id="memory-edit-error">${model.error()}</p>@endif
        <button class="button is-primary" data-action="save">Save</button>
      </form>
      @template.ui.deleteConfirmation(action = "/memory/" + model.memory().id() + "/delete", item = model.memory().text(), csrfName = model.csrf().getParameterName(), csrfValue = model.csrf().getToken(), consequence = "This memory will no longer be available for recall.")
    </div>
  </main>
</body>
</html>
