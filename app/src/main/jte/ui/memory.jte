@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.web.ui.MemoryController.MemoryModel
@import org.zalava.knowledge.memory.domain.Memory
@import org.zalava.knowledge.memory.domain.MemoryProposal
@import org.zalava.knowledge.memory.domain.MemoryScope
@import org.zalava.web.ui.LibraryPresentation
@param MemoryModel model
@param NavigationModel navigation
@param String memoryMessage
@param String memoryError
@param String proposalText = ""
@param String proposalScope = "USER"
@param String proposalError = null
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
      <header class="mb-5">
        <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7">Recall</p>
        <h1 class="title is-3">Memory</h1>
        <p class="has-text-grey">Private to your account. Approved memories help Zalava recall relevant preferences and knowledge.</p>

      </header>
      @if(memoryMessage != null)
        <article class="message is-success" role="status"><div class="message-body">${memoryMessage}</div></article>
      @endif
      @if(memoryError != null)
        <article class="message is-danger" role="alert" tabindex="-1" data-error-summary><div class="message-body">${memoryError}</div></article>
      @endif
      @if(model.error() != null)
        <p class="notification is-danger is-light">${model.error()}</p>
      @endif
      <form method="post" action="/memory/save" class="box">
        <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
        <h2 class="title is-5">Add a memory</h2>
        <p class="has-text-grey is-size-7 mb-3">Memories you enter here are saved immediately. Suggestions from Chat need your approval.</p>
        <details class="memory-options"><summary>Optional category</summary>        <details class="mt-3"><summary>What do the scopes mean?</summary><ul class="content mt-3"><li><strong>Personal preferences</strong>: facts and preferences about you.</li><li><strong>Project knowledge</strong>: information about your projects.</li><li><strong>Agent knowledge</strong>: lessons you want Zalava to use.</li><li><strong>Execution trace</strong>: temporary run information, excluded from durable recall.</li></ul><p>All of these scopes stay private to your account; a scope does not share a memory with other members.</p></details>
        <div class="field">
          <label class="label" for="proposal-scope">Scope <span class="field-requirement">(required)</span></label>
          <div class="control">
            <div class="select">
              <select id="proposal-scope" name="scope">
                @for(MemoryScope option : LibraryPresentation.durableScopes())
                  <option value="${option.name()}" selected="${option.name().equals(proposalScope)}">${LibraryPresentation.scope(option)}</option>
                @endfor
              </select>
            </div>
          </div>
        </div>
        </details>
        <div class="field">
          <label class="label" for="proposal-text">Memory <span class="field-requirement">(required)</span></label>
          <div class="control"><textarea class="textarea" id="proposal-text" name="text" maxlength="2000" required aria-invalid="${proposalError != null ? "true" : null}" aria-describedby="${proposalError != null ? "proposal-help proposal-error" : "proposal-help"}">${proposalText}</textarea></div>
        </div>
        <p class="help" id="proposal-help">Up to 2,000 characters. Do not include passwords or credentials.</p>
        @if(proposalError != null)<p class="field-error" id="proposal-error">${proposalError}</p>@endif
        <button class="button is-primary" data-action="save-memory">Save memory</button>
      </form>
      <form method="get" action="/memory" class="library-search memory-search">
        <div class="field">
          <label class="label" for="memory-query">Search memory</label><input id="memory-query" class="input" type="search" name="query" value="${model.query()}" maxlength="256" aria-label="Search memory">
        </div>
        <details class="memory-options" open="${!model.scope().isBlank()}"><summary>Filter by category</summary>
        <div class="field"><label class="label" for="memory-filter-scope">Scope</label>
          <div class="select is-fullwidth">
            <select id="memory-filter-scope" name="scope" aria-label="Filter by scope">
              @if(model.scope().isEmpty())
                <option value="" selected>All scopes</option>
              @else
                <option value="">All scopes</option>
              @endif
              @for(MemoryScope option : MemoryScope.values())
                @if(option.name().equals(model.scope()))
                  <option value="${option.name()}" selected>${LibraryPresentation.scope(option)}</option>
                @else
                  <option value="${option.name()}">${LibraryPresentation.scope(option)}</option>
                @endif
              @endfor
            </select>
          </div>
        </div>
        </details>
        <div class="control"><button class="button is-primary">Search</button></div>
      </form>
      <h2 class="title is-5">Pending proposals</h2>
      @if(model.proposals().isEmpty())
        <div class="empty-state"><p>No pending proposals.</p><p>Ask Zalava to remember something in Chat. Review its suggestion here before it is saved.</p></div>
      @else
        <ul>
        @for(MemoryProposal proposal : model.proposals())
          <li class="box" id="proposal-${proposal.id()}" data-memory-proposal="${proposal.id()}">
            <p class="has-text-weight-bold">${proposal.text()}</p>
            <span class="is-size-7 has-text-grey" data-proposal-scope>${LibraryPresentation.scope(proposal.scope())} · Awaiting your review · <time datetime="${proposal.createdAt()}">${LibraryPresentation.date(proposal.createdAt())}</time></span>
            <div class="mt-3">
              <form method="post" action="/memory/proposals/${proposal.id()}/approve" class="is-inline-block">
                <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
                <button class="button is-small is-primary" data-action="approve">Approve</button>
              </form>
              <form method="post" action="/memory/proposals/${proposal.id()}/reject" class="is-inline-block">
                <input type="hidden" name="${model.csrf().getParameterName()}" value="${model.csrf().getToken()}">
                <button class="button is-small" data-action="reject">Reject</button>
              </form>

            </div>
          </li>
        @endfor
        </ul>
      @endif
      <h2 class="title is-5">Memories</h2>
      @if(model.memories().isEmpty())
        <section class="empty-state" data-memory-empty>
        @if(!model.query().isBlank() || !model.scope().isBlank())
          <p class="has-text-weight-bold">No matching memories.</p><a class="button is-light" href="/memory">Clear filters</a>
        @else
          <p class="has-text-weight-bold">No memories yet.</p><p>Save a memory above to make it available for recall.</p><a class="button is-light" href="#proposal-text">Add a memory</a>
        @endif
        </section>
      @else
        <ul>
        @for(Memory memory : model.memories())
          <li class="box" data-memory-entry="${memory.id()}">
            <a class="has-text-weight-bold" href="/memory/${memory.id()}">${memory.text()}</a><br>
            <span class="is-size-7 has-text-grey" data-memory-scope>${LibraryPresentation.scope(memory.scope())} · ${LibraryPresentation.provenance(memory.provenance().source())}@if(memory.provenance().reference() != null) · ${memory.provenance().reference()}@endif · created <time datetime="${memory.createdAt().toString()}">${LibraryPresentation.date(memory.createdAt())}</time>@if(memory.updatedAt() != null) · revised <time datetime="${memory.updatedAt().toString()}">${LibraryPresentation.date(memory.updatedAt())}</time>@endif</span>
            @template.ui.deleteConfirmation(action = "/memory/" + memory.id() + "/delete", item = memory.text(), csrfName = model.csrf().getParameterName(), csrfValue = model.csrf().getToken(), consequence = "This memory will no longer be available for recall.")
          </li>
        @endfor
        </ul>
      @endif
    </div>
  </main>
</body>
</html>
