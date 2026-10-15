/* Shared native-form feedback, navigation and accessible disclosure cancellation. */
(() => {
  const pendingForms = new Map();
  document.addEventListener('submit', event => {
    const form = event.target;
    if (event.defaultPrevented || !(form instanceof HTMLFormElement) || form.hasAttribute('data-repeatable')) return;
    if (pendingForms.has(form)) { event.preventDefault(); return; }
    if (!form.checkValidity()) return;
    const submitter = event.submitter;
    if (submitter && submitter.name) {
      const value = document.createElement('input');
      value.type = 'hidden'; value.name = submitter.name; value.value = submitter.value;
      value.dataset.pendingSubmitter = 'true'; form.appendChild(value);
    }
    const buttons = Array.from(form.elements).filter(el => ['submit', 'image'].includes(el.type));
    pendingForms.set(form, buttons.map(el => ({el, disabled: el.disabled, html: el.innerHTML, value: el.value})));
    buttons.forEach(button => {
      button.disabled = true; button.setAttribute('aria-disabled', 'true'); button.dataset.pending = 'true';
      if (button.tagName === 'BUTTON') button.textContent = 'Working…';
    });
    form.setAttribute('aria-busy', 'true');
    const status = document.createElement('p');
    status.className = 'zalava-action-status'; status.setAttribute('role', 'status');
    status.dataset.actionStatus = 'true'; status.textContent = 'Zalava is working on your request.';
    form.appendChild(status);
  });
  window.addEventListener('pageshow', () => {
    pendingForms.forEach((buttons, form) => {
      buttons.forEach(({el, disabled, html, value}) => {
        el.disabled = disabled; el.removeAttribute('aria-disabled'); delete el.dataset.pending;
        if (el.tagName === 'BUTTON') el.innerHTML = html; else el.value = value;
      });
      form.removeAttribute('aria-busy');
      form.querySelectorAll('[data-action-status], [data-pending-submitter]').forEach(el => el.remove());
    });
    pendingForms.clear();
  });
  document.addEventListener('click', event => {
    const cancel = event.target.closest('[data-cancel-confirmation]');
    if (!cancel) return;
    const details = cancel.closest('details'); details.open = false; details.querySelector('summary').focus();
  });
  const burger = document.querySelector('.navbar-burger');
  const menu = document.getElementById('zalava-product-navigation');
  if (burger && menu) {
    let previousFocus;
    const close = () => { menu.classList.remove('is-active'); burger.classList.remove('is-active'); burger.setAttribute('aria-expanded', 'false'); previousFocus?.focus(); };
    burger.addEventListener('click', () => {
      if (menu.classList.contains('is-active')) return close();
      previousFocus = document.activeElement; menu.classList.add('is-active'); burger.classList.add('is-active'); burger.setAttribute('aria-expanded', 'true');
    });
    menu.querySelector('.zalava-navigation-close')?.addEventListener('click', close);
    menu.querySelectorAll('a').forEach(link => link.addEventListener('click', close));
    document.addEventListener('keydown', event => { if (event.key === 'Escape' && menu.classList.contains('is-active')) close(); });
  }
  document.querySelector('[data-error-summary]')?.focus();
})();
