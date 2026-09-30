@import org.zalava.ui.MemoryController.MemoryDetailModel
@import org.zalava.memory.domain.MemoryScope
@param MemoryDetailModel model
@param org.zalava.ui.navigation.NavigationModel navigation
@param String memoryMessage
@param String memoryError
<!doctype html>
<html lang="en" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Zalava Memory</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
  <link rel="stylesheet" href="/sea-ui.css">
</head>
<body>
  @template.ui.navigation(items = navigation.items(), activePage = "memory")
  <main class="section">
    <div class="container">
      <p class="mb-4"><a href="/memory">Back to memory</a></p>
      @if(memoryMessage != null)
        <article class="message is-success" role="status"><div class="message-body">${memoryMessage}</div></article>
      @endif
      @if(model.error() != null)
        <article class="message is-danger" role="alert"><div class="message-body">${model.error()}</div></article>
      @endif
      <h1 class="title is-3">Memory entry</h1>
      <div class="box" data-memory-detail="${model.memory().id()}">
        <dl>
          <dt class="has-text-weight-bold">Id</dt><dd data-memory-id>${model.memory().id()}</dd>
          <dt class="has-text-weight-bold">Text</dt><dd data-memory-text>${model.memory().text()}</dd>
          <dt class="has-text-weight-bold">Scope</dt><dd data-memory-scope>${model.memory().scope()}</dd>
          <dt class="has-text-weight-bold">Created</dt><dd>${model.memory().createdAt().toString()}</dd>
          @if(model.memory().updatedAt() != null)
            <dt class="has-text-weight-bold">Last revised</dt><dd data-memory-updated>${model.memory().updatedAt().toString()}</dd>
          @endif
          <dt class="has-text-weight-bold">Provenance</dt><dd data-memory-provenance>${model.memory().provenance().source()}@if(model.memory().provenance().reference() != null) · ${model.memory().provenance().reference()}@endif</dd>
          @if(!model.memory().metadata().isEmpty())
            <dt class="has-text-weight-bold">Metadata</dt>
            <dd>
              @for(var entry : model.memory().metadata().entrySet())
                <span>${entry.getKey()}: ${entry.getValue()}</span><br>
              @endfor
            </dd>
          @endif
        </dl>
      </div>
      <form method="post" action="/memory/${model.memory().id()}/edit" class="box">
        <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
        <h2 class="title is-5">Edit memory</h2>
        <div class="field">
          <label class="label" for="memory-scope">Scope</label>
          <div class="control">
            <div class="select">
              <select id="memory-scope" name="scope">
                @for(MemoryScope option : MemoryScope.durableScopes())
                  @if(option.name().equals(model.scope()))
                    <option value="${option.name()}" selected>${option.name()}</option>
                  @else
                    <option value="${option.name()}">${option.name()}</option>
                  @endif
                @endfor
              </select>
            </div>
          </div>
        </div>
        <div class="field">
          <label class="label" for="memory-text">Memory</label>
          <div class="control"><textarea class="textarea" id="memory-text" name="text" maxlength="2000" required>${model.text()}</textarea></div>
        </div>
        <button class="button is-primary" data-action="save">Save</button>
      </form>
      <form method="post" action="/memory/${model.memory().id()}/delete">
        <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
        <input type="hidden" name="confirmed" value="true">
        <button class="button is-danger is-light" data-action="delete">Delete</button>
      </form>
    </div>
  </main>
</body>
</html>
