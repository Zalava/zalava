@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.web.ui.MemoryController.MemoryModel
@import org.zalava.knowledge.memory.domain.Memory
@import org.zalava.knowledge.memory.domain.MemoryProposal
@import org.zalava.knowledge.memory.domain.MemoryScope
@param MemoryModel model
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
  <link rel="stylesheet" href="/zalava-ui.css">
</head>
<body>
  @template.ui.navigation(items = navigation.items(), activePage = "memory")
  <main class="section">
    <div class="container">
      <header class="mb-5">
        <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7">Recall</p>
        <h1 class="title is-3">Memory</h1>
        <p class="has-text-grey">What Zalava remembers for you, with its scope and provenance.</p>
      </header>
      @if(memoryMessage != null)
        <article class="message is-success" role="status"><div class="message-body">${memoryMessage}</div></article>
      @endif
      @if(memoryError != null)
        <article class="message is-danger" role="alert"><div class="message-body">${memoryError}</div></article>
      @endif
      @if(model.error() != null)
        <p class="notification is-danger is-light">${model.error()}</p>
      @endif
      <form method="post" action="/memory/proposals" class="box">
        <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
        <h2 class="title is-5">Propose a memory</h2>
        <p class="has-text-grey is-size-7 mb-3">Proposals stay pending until you approve them; the model can only propose.</p>
        <div class="field">
          <label class="label" for="proposal-scope">Scope</label>
          <div class="control">
            <div class="select">
              <select id="proposal-scope" name="scope">
                @for(MemoryScope option : MemoryScope.durableScopes())
                  <option value="${option.name()}">${option.name()}</option>
                @endfor
              </select>
            </div>
          </div>
        </div>
        <div class="field">
          <label class="label" for="proposal-text">Memory</label>
          <div class="control"><textarea class="textarea" id="proposal-text" name="text" maxlength="2000" required></textarea></div>
        </div>
        <button class="button is-primary" data-action="propose">Propose</button>
      </form>
      <form method="get" action="/memory" class="field has-addons">
        <div class="control is-expanded">
          <input class="input" type="search" name="query" value="${model.query()}" maxlength="256" aria-label="Search memory">
        </div>
        <div class="control">
          <div class="select">
            <select name="scope" aria-label="Filter by scope">
              @if(model.scope().isEmpty())
                <option value="" selected>All scopes</option>
              @else
                <option value="">All scopes</option>
              @endif
              @for(MemoryScope option : MemoryScope.all())
                @if(option.name().equals(model.scope()))
                  <option value="${option.name()}" selected>${option.name()}</option>
                @else
                  <option value="${option.name()}">${option.name()}</option>
                @endif
              @endfor
            </select>
          </div>
        </div>
        <div class="control"><button class="button is-primary">Search</button></div>
      </form>
      <h2 class="title is-5">Pending proposals</h2>
      @if(model.proposals().isEmpty())
        <p class="has-text-grey">No pending proposals.</p>
      @else
        <ul>
        @for(MemoryProposal proposal : model.proposals())
          <li class="box" data-memory-proposal="${proposal.id()}">
            <p class="has-text-weight-bold">${proposal.text()}</p>
            <span class="is-size-7 has-text-grey" data-proposal-scope>${proposal.scope()} · ${proposal.provenance().source()} · ${proposal.createdAt()}</span>
            <div class="mt-3">
              <form method="post" action="/memory/proposals/${proposal.id()}/approve" class="is-inline-block">
                <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
                <button class="button is-small is-primary" data-action="approve">Approve</button>
              </form>
              <form method="post" action="/memory/proposals/${proposal.id()}/reject" class="is-inline-block">
                <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
                <button class="button is-small" data-action="reject">Reject</button>
              </form>
              <form method="post" action="/memory/proposals/${proposal.id()}/revoke" class="is-inline-block">
                <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
                <button class="button is-small is-danger is-light" data-action="revoke">Revoke</button>
              </form>
            </div>
          </li>
        @endfor
        </ul>
      @endif
      <h2 class="title is-5">Memories</h2>
      @if(model.memories().isEmpty())
        <p class="has-text-grey" data-memory-empty>No memories.</p>
      @else
        <ul>
        @for(Memory memory : model.memories())
          <li class="box" data-memory-entry="${memory.id()}">
            <a class="has-text-weight-bold" href="/memory/${memory.id()}">${memory.text()}</a><br>
            <span class="is-size-7 has-text-grey" data-memory-scope>${memory.scope()} · ${memory.provenance().source()}@if(memory.provenance().reference() != null) · ${memory.provenance().reference()}@endif · created ${memory.createdAt().toString()}@if(memory.updatedAt() != null) · revised ${memory.updatedAt().toString()}@endif</span>
            <form method="post" action="/memory/${memory.id()}/delete" class="mt-3">
              <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
              <input type="hidden" name="confirmed" value="true">
              <button class="button is-small is-danger is-light" data-action="delete">Delete</button>
            </form>
          </li>
        @endfor
        </ul>
      @endif
    </div>
  </main>
</body>
</html>
