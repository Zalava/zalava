@import java.util.List
@import org.zalava.identity.accounts.api.ManagedAccountView
@import org.zalava.identity.accounts.domain.AccountRole
@import org.zalava.web.ui.navigation.NavigationModel
@import org.springframework.security.web.csrf.CsrfToken
@param List<ManagedAccountView> accounts
@param String error = null
@param String message = null
@param String createLogin = ""
@param AccountRole createRole = AccountRole.MEMBER
@param CsrfToken csrf
@param NavigationModel navigation
<!doctype html>
<html lang="en" data-theme="light">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Zalava accounts</title><link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css"><link rel="stylesheet" href="/zalava-tokens.css">
<link rel="stylesheet" href="/zalava-ui.css">
<script src="/zalava-ui.js" defer></script></head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "settings", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken(), accountName = navigation.accountName())
<main class="section"><div class="container accounts-layout">
<header><p class="has-text-primary has-text-weight-bold is-uppercase is-size-7">Administration</p><h1 class="title is-3">Accounts</h1><p class="has-text-grey">Manage workspace access. Only administrators can change account roles and reset passwords.</p></header>
@if(error != null)<p class="notification is-danger is-light" role="alert">${error}</p>@endif
@if(message != null)<p class="notification is-success is-light" role="status">${message}</p>@endif
<section class="box" aria-labelledby="create-account-heading"><h2 class="title is-4" id="create-account-heading">Create account</h2>
<form method="post" action="/zalava/accounts/create" class="account-create-form">
<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
<div class="field"><label class="label" for="create-login">Login</label><input class="input" id="create-login" name="loginName" value="${createLogin}" required maxlength="100" autocomplete="username" aria-describedby="login-help"><p class="help" id="login-help">Letters, numbers, dots, underscores and hyphens.</p></div>
<div class="field"><label class="label" for="create-password">Temporary password</label><input class="input" id="create-password" data-password-original type="password" name="temporaryPassword" required minlength="12" maxlength="256" autocomplete="new-password"><p class="help">At least 12 characters. Share it privately; the member replaces it at sign-in.</p></div>
<div class="field"><label class="label" for="confirm-create-password">Confirm temporary password</label><input class="input" id="confirm-create-password" data-password-confirmation type="password" name="passwordConfirmation" autocomplete="new-password" required minlength="12" maxlength="256"></div>
<div class="field"><label class="label" for="create-role">Role</label><div class="select is-fullwidth"><select id="create-role" name="role"><option value="MEMBER" selected="${createRole == AccountRole.MEMBER}">Member</option><option value="ADMIN" selected="${createRole == AccountRole.ADMIN}">Administrator</option></select></div></div>
<div><button class="button is-primary" type="submit">Create account</button></div></form></section>
<section aria-labelledby="managed-accounts-heading"><h2 class="title is-4" id="managed-accounts-heading">Managed accounts</h2><div class="account-list">
@for(var account : accounts)
<article class="box account-card" data-account-id="${account.id()}">
<header class="account-card-heading"><h3 class="title is-5">${account.loginName()}</h3><div class="tags"><span class="tag">${account.role() == AccountRole.ADMIN ? "Administrator" : "Member"}</span><span class="tag ${account.enabled() ? "is-success is-light" : "is-light"}">${account.enabled() ? "Enabled" : "Disabled"}</span><span class="tag is-light">${account.passwordChangeRequired() ? "Password change required" : "Password set"}</span></div></header>
<div class="account-actions"><form method="post" action="/zalava/accounts/role"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="accountId" value="${account.id()}"><input type="hidden" name="role" value="${account.role() == AccountRole.ADMIN ? "MEMBER" : "ADMIN"}"><button class="button is-light" type="submit">${account.role() == AccountRole.ADMIN ? "Make member" : "Make administrator"}</button></form>
<form method="post" action="/zalava/accounts/enabled"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="accountId" value="${account.id()}"><input type="hidden" name="enabled" value="${account.enabled() ? "false" : "true"}"><button class="button is-light" type="submit">${account.enabled() ? "Disable" : "Enable"}</button></form></div>
<details class="account-password-reset"><summary>Reset password</summary><form method="post" action="/zalava/accounts/password"><input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}"><input type="hidden" name="accountId" value="${account.id()}"><label class="label" for="reset-${account.id()}">Temporary password</label><input class="input" id="reset-${account.id()}" data-password-original type="password" name="temporaryPassword" required minlength="12" maxlength="256" autocomplete="new-password"><label class="label" for="confirm-reset-${account.id()}">Confirm temporary password</label><input class="input" id="confirm-reset-${account.id()}" data-password-confirmation type="password" name="passwordConfirmation" autocomplete="new-password" required minlength="12" maxlength="256"><button class="button is-light mt-3" type="submit">Reset password</button></form></details>
</article>
@endfor
</div></section></div></main></body></html>
