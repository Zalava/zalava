@param String title
@param String content
@param org.springframework.security.web.csrf.CsrfToken csrf = null
@param org.zalava.web.ui.navigation.NavigationModel navigation

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title}</title>
  @if(csrf != null)
    <meta name="sea-csrf-parameter" content="${csrf.getParameterName()}">
    <meta name="sea-csrf-token" content="${csrf.getToken()}">
  @endif
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
    <link rel="stylesheet" href="/sea-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "apps")

<main class="section">
    <div class="container">$unsafe{content}</div>
</main>

@if(csrf != null)
<script>
(function () {
  const parameter = document.querySelector('meta[name="sea-csrf-parameter"]').content;
  const token = document.querySelector('meta[name="sea-csrf-token"]').content;
  // Populate before native form.submit(), which bypasses submit event listeners.
  document.querySelectorAll('main form').forEach(function (form) {
    if (form.method.toLowerCase() !== 'post' || new URL(form.action).origin !== location.origin) return;
    let field = Array.from(form.elements).find(function (element) { return element.name === parameter; });
    if (!field) {
      field = document.createElement('input');
      field.type = 'hidden';
      field.name = parameter;
      form.appendChild(field);
    }
    field.value = token;
  });
})();
</script>
@endif
</body>
</html>
