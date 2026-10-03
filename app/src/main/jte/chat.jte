@param org.zalava.web.ui.navigation.NavigationModel navigation

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
<title>Zalava Chat</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css">
    <link rel="stylesheet" href="/zalava-ui.css">
    <link rel="stylesheet" href="/zalava-chat/assets/zalava-chat.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "chat")

<div id="root"></div>

<script type="module" src="/zalava-chat/assets/zalava-chat.js"></script>
</body>
</html>
