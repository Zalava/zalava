@import org.springframework.security.web.csrf.CsrfToken
@import org.zalava.web.ui.navigation.NavigationModel
@param String title
@param String content
@param CsrfToken csrf = null
@param NavigationModel navigation

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title}</title>
  @if(csrf != null)
    <meta name="zalava-csrf-parameter" content="${csrf.getParameterName()}">
    <meta name="zalava-csrf-token" content="${csrf.getToken()}">
  @endif
    <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
    <link rel="stylesheet" href="/zalava-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "apps", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken())

<main class="section">
    <div class="container">$unsafe{content}</div>
</main>

@if(csrf != null)
<script>
(function () {
  const parameter = document.querySelector('meta[name="zalava-csrf-parameter"]').content;
  const token = document.querySelector('meta[name="zalava-csrf-token"]').content;
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
