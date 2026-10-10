@import org.zalava.web.ui.navigation.NavigationModel
@import org.zalava.web.ui.ModulesController.ModuleConfigurationModel
@import org.zalava.web.ui.ModuleConfigurationForm.Field
@import org.springframework.security.web.csrf.CsrfToken

@param ModuleConfigurationModel model
@param String configurationMessage
@param String configurationError
@param CsrfToken csrf
@param NavigationModel navigation

<!doctype html>
<html lang="en" data-theme="light">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
 <title>Configure ${model.displayName()} · Zalava</title>
    <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
    <link rel="stylesheet" href="/zalava-ui.css">
</head>
<body>
@template.ui.navigation(items = navigation.items(), activePage = "modules", csrfName = navigation.csrfParameter(), csrfValue = navigation.csrfToken())
<main class="section">
    <div class="container">
        <a class="is-size-7" href="/modules">← Modules</a>
        <header class="mt-3 mb-5">
            <p class="has-text-primary has-text-weight-bold is-uppercase is-size-7 mb-1">Module configuration</p>
            <h1 class="title is-3 mb-2">${model.displayName()}</h1>
            <p class="has-text-grey">${model.moduleId()}</p>
            <span class="tag ${model.configuration().cssClass()} mt-3" data-configuration-status>${model.configuration().label()}</span>
        </header>

        @if(configurationMessage != null)
            <article class="message is-success" role="status"><div class="message-body">${configurationMessage}</div></article>
        @endif
        @if(configurationError != null)
            <article class="message is-danger" role="alert"><div class="message-body">${configurationError}</div></article>
        @endif

        <section class="box">
            @if(model.fields().isEmpty())
                <h2 class="title is-5">No configuration required</h2>
                <p class="has-text-grey">This module does not declare configurable factory fields.</p>
            @else
                <div class="level is-mobile">
                    <div class="level-left"><h2 class="title is-5 mb-2">Setup and edit</h2></div>
                    <div class="level-right"><span class="tag is-light" data-stored-secret-count>${model.storedSecretCount()} stored secret reference(s)</span></div>
                </div>
                <p class="has-text-grey mb-5">Valid changes apply when saved. Stored secret values are never shown; enter a replacement only when it changes.</p>
                <form method="post" action="/modules/${model.moduleId()}/configuration">
                    <input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">
                    @for(Field field : model.fields())
                        <div class="field">
                            <label class="label" for="${field.name()}">${field.label()}</label>
                            @if(field.description() != null)
                                <p class="help mb-2">${field.description()}</p>
                            @endif
                            <div class="control">
                                @if(field.type().equals("boolean"))
                                    <label class="checkbox"><input id="${field.name()}" name="${field.name()}" type="checkbox" value="true" checked="${field.value().equals("true")}"> Enabled</label>
                                @endif
                                @if(field.type().equals("json"))
                                    <textarea id="${field.name()}" class="textarea is-family-monospace" name="${field.name()}" rows="5" required="${field.required()}">${field.value()}</textarea>
                                @endif
                                @if(!field.type().equals("boolean") && !field.type().equals("json"))
                                    @if(field.required())
                                        <input id="${field.name()}" class="input" name="${field.name()}" type="${field.type().equals("number") || field.type().equals("integer") ? "number" : "text"}" value="${field.value()}" required>
                                    @else
                                        <input id="${field.name()}" class="input" name="${field.name()}" type="${field.type().equals("number") || field.type().equals("integer") ? "number" : "text"}" value="${field.value()}">
                                    @endif
                                @endif
                            </div>
                            @if(field.secretReference())
                                <div class="control mt-2">
                                    <label class="label is-size-7" for="${field.replacementSecretName()}">Replace stored secret</label>
                                    <input id="${field.replacementSecretName()}" class="input" name="${field.replacementSecretName()}" type="password" autocomplete="new-password" data-secret-replacement="${field.path()}">
                                    <p class="help">Leave blank to keep the stored value. Zalava never renders it.</p>
                                </div>
                            @endif
                        </div>
                    @endfor
                    <div class="field is-grouped mt-5">
                        <div class="control"><button class="button is-primary" type="submit">Save configuration</button></div>
                        @if(model.candidatePresent())
                            <div class="control"><button class="button is-light" type="submit" formaction="/modules/${model.moduleId()}/configuration/cancel">Discard pending changes</button></div>
                        @endif
                    </div>
                </form>
            @endif
        </section>
    </div>
</main>
</body>
</html>
