@import org.springframework.security.web.csrf.CsrfToken
@param String error
@param CsrfToken csrf
<!doctype html><html lang="en" data-theme="light"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Change Zalava password</title><link rel="stylesheet" href="/zalava-ui.css"></head>
<body class="zalava-auth"><main class="auth-layout">
<section class="auth-introduction"><span class="auth-brand"><span class="zalava-brand-mark">Z</span>Zalava</span>
<p class="auth-eyebrow">Your workspace account</p><h1>Make your account yours.</h1><p>Choose a new password before continuing to your workspace.</p></section>
<section class="auth-card" aria-labelledby="password-heading"><h2 id="password-heading">Change password</h2><p>Your current password is temporary or has been reset. Choose at least 12 characters.</p>
@if(error != null)<p class="auth-alert" role="alert">${error}. Check your current password and use at least 12 characters for the new password.</p>@endif
<form method="post" action="/account/password"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
<label for="current-password">Current password</label><input id="current-password" type="password" name="currentPassword" autocomplete="current-password" required>
<label for="new-password">New password</label><input id="new-password" type="password" name="replacementPassword" autocomplete="new-password" required minlength="12" aria-describedby="password-help">
<p id="password-help" class="auth-help">Use a unique password with at least 12 characters.</p><button type="submit">Change password</button></form>
<form method="post" action="/logout"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><button class="auth-secondary" type="submit">Sign out</button></form></section>
</main></body></html>
