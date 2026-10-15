@param String heading
@param int statusCode
<!doctype html><html lang="en" data-theme="light"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Zalava — ${heading}</title><link rel="stylesheet" href="/zalava-tokens.css">
<link rel="stylesheet" href="/zalava-ui.css">
<script src="/zalava-ui.js" defer></script></head>
<body class="zalava-auth"><main class="auth-layout">
<section class="auth-introduction"><a class="auth-brand" href="/chat"><span class="zalava-brand-mark">Z</span>Zalava</a><p class="auth-eyebrow">Your workspace</p><h1>${heading}</h1><p>The page may be unavailable, or the action may require different access. Your private content stays protected.</p></section>
<section class="auth-card"><h2>Choose where to go next</h2><p>You can return to Chat, or sign in with your workspace account.</p><p class="auth-help">Error ${statusCode}</p><a class="auth-recovery" href="/chat">Return to Chat</a><a class="auth-recovery auth-secondary" href="/login">Sign in</a></section>
</main></body></html>
