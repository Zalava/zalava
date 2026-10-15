@param String action
@param String item
@param String csrfName
@param String csrfValue
@param String consequence
@param String label = "Delete"
@param String actionLabel = "Delete permanently"
@param boolean deletion = true
<details class="destructive-confirmation">
  <summary class="button is-danger is-light" data-action="${deletion ? "delete" : "confirm-action"}">${label}</summary>
  <div class="confirmation-panel" role="group" aria-label="Confirm action">
    <p><strong>${label} ${item}?</strong></p>
    <p>${consequence} This cannot be undone.</p>
    <form method="post" action="${action}">
      <input type="hidden" name="${csrfName}" value="${csrfValue}">
      <input type="hidden" name="confirmed" value="true">
      <div class="confirmation-actions">
        <button class="button is-danger" type="submit" data-action="${deletion ? "confirm-delete" : "confirm-action-submit"}">${actionLabel}</button>
        <button class="button is-light" type="button" data-cancel-confirmation>Cancel</button>
      </div>
    </form>
  </div>
</details>
