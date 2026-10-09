@import org.zalava.web.ui.navigation.NavigationModel
@param NavigationModel navigation
@param boolean providerConfigured
@param boolean providerSetupAllowed

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
<title>Zalava Chat</title>
    <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
    <link rel="stylesheet" href="/zalava-ui.css">
    <link rel="stylesheet" href="/zalava-chat/assets/zalava-chat.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "chat")

<div id="root" data-provider-configured="${providerConfigured}" data-provider-setup-allowed="${providerSetupAllowed}"></div>

<script type="module" src="/zalava-chat/assets/zalava-chat.js"></script>
</body>
</html>
