@import org.springframework.security.web.csrf.CsrfToken
@param CsrfToken csrf
@param boolean error
@param boolean loggedOut
<!doctype html>
<html lang="en" data-theme="light">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Zalava sign in</title><link rel="stylesheet" href="/zalava-ui.css"></head>
<body class="zalava-auth"><main class="auth-layout">
<section class="auth-introduction"><a class="auth-brand" href="/login"><span class="zalava-brand-mark">Z</span>Zalava</a>
<p class="auth-eyebrow">Your workspace assistant</p><h1>A place to think.<br>A way to get things done.</h1>
<p>Ask questions, organize knowledge and follow your work in one workspace.</p></section>
<section class="auth-card" aria-labelledby="sign-in-heading"><h2 id="sign-in-heading">Sign in</h2><p>Welcome back to your workspace.</p>
@if(error)<p class="auth-alert" role="alert">The login or password was not recognized. Try again.</p>@endif
@if(loggedOut)<p class="auth-notice" role="status">You have signed out.</p>@endif
<form method="post" action="/login"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
<label for="username">Login</label><input id="username" name="username" autocomplete="username" required autofocus>
<label for="password">Password</label><input id="password" type="password" name="password" autocomplete="current-password" required>
<button type="submit">Sign in</button></form><p class="auth-help">Use the account provided by your workspace administrator.</p></section>
</main></body></html>
