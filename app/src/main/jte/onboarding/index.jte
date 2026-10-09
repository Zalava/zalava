@import org.zalava.web.onboarding.steps.S5StarterModulesStep.StarterModule
@import java.util.List
@import java.util.Map
@import org.zalava.SupportedProvider
@import org.springframework.security.web.csrf.CsrfToken

@param String currentStep
@param Integer currentStepNumber
@param Integer totalSteps
@param List<Map<String, Object>> steps
@param String previousStepUrl
@param String nextStepUrl
@param Boolean isOptional
@param String stepTemplate
@param CsrfToken csrf
@param String error
@param List<SupportedProvider> providers
@param String selectedProvider
@param String providerLabel
@param Boolean requiresApiKey
@param String systemWideTokenName
@param String apiKey
@param String model
@param String chatModelPropertyKey
@param String providerApiPropertyKey
@param String agentContent
@param List<StarterModule> starterModules
@param String selectedStarterModuleId
@param Boolean selectedStarterConfigured

<html lang="en">
<head>
    <title>Zalava Setup</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="/css/vendor/bulma-1.0.4.min.css">
    <style>
    .brand-lockup {
        border-left: 3px solid var(--bulma-primary);
        padding-left: .65rem;
        line-height: 1.15;
    }
    .brand-word {
        font-size: 1.1rem;
        font-weight: 800;
    }
    .brand-word .brand-agent { color: #fff; }
    .brand-word .brand-runr  { color: var(--bulma-primary); }
    </style>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@samlam140330/bulma-timeline@1.0.3/dist/css/bulma-timeline.min.css">
    <style>
        .provider-card {
            border: 2px solid var(--bulma-border);
            border-radius: var(--bulma-radius-large);
            padding: 1rem;
            transition: border-color 140ms ease, box-shadow 140ms ease;
            cursor: pointer;
            display: block;
            background: var(--bulma-white);
        }

        .provider-card:hover {
            border-color: var(--bulma-primary);
            box-shadow: var(--bulma-shadow);
        }

        .provider-card.is-selected {
            border-color: var(--bulma-primary);
            box-shadow: var(--bulma-shadow);
        }

        .step-fragment {
            animation: step-enter 220ms ease;
        }

        textarea.textarea.is-agent-editor {
            min-height: 22rem;
            font-family: "SFMono-Regular", "Menlo", monospace;
        }

        .htmx-indicator {
            opacity: 0;
            transition: opacity 120ms ease;
        }

        .htmx-request .htmx-indicator,
        .htmx-request.htmx-indicator {
            opacity: 1;
        }

        @@keyframes step-enter {
            from { opacity: 0; transform: translateY(8px); }
            to   { opacity: 1; transform: translateY(0); }
        }

        .sidebar-timeline .timeline-item::before {
            background-color: rgba(255, 255, 255, 0.15);
        }
        .sidebar-timeline .timeline-item .timeline-marker {
            background: rgba(255, 255, 255, 0.18) !important;
            border-color: rgba(255, 255, 255, 0.3) !important;
        }
        .sidebar-timeline .timeline-item .timeline-marker.is-icon > * {
            color: rgba(255, 255, 255, 0.75) !important;
        }
        div.sidebar-timeline .timeline-item {
            padding-bottom: 0.25em;
        }
        div.sidebar-timeline .timeline-item .timeline-content {
            padding-top: 1.2em;
        }
    </style>
</head>
<body hx-boost="true">
<div id="content">
    <section class="section py-5">
        <div class="container is-max-desktop">
            <div class="columns is-variable is-6">
                <div class="column is-5">
                    <aside class="box has-background-dark has-text-white">
                        <div class="brand-lockup mb-5">
                            <p class="is-size-7 has-text-primary has-text-weight-bold is-uppercase mb-1" style="letter-spacing: 0.1em">Setup Wizard</p>
                            <p class="brand-word mb-0"><span class="brand-agent">S</span><span class="brand-runr">EA</span></p>
                        </div>
                        <h1 class="title is-3 has-text-white mb-3">Turn a blank install into a ready-to-run assistant.</h1>
                        <p class="has-text-grey-light mb-5">Choose a model provider, save credentials, tune your workspace instructions, and optionally configure a curated starter module.</p>
                        <p class="is-size-7 has-text-grey mb-5 has-text-centered">Powered by <a href="https://spring.io/projects/spring-ai" target="_blank" rel="noopener noreferrer" class="has-text-grey-light">Spring AI</a> &amp; <a href="https://www.jobrunr.io" target="_blank" rel="noopener noreferrer" class="has-text-grey-light">JobRunr</a></p>

                        <div class="timeline no-headers sidebar-timeline">
                            @for(int stepIndex = 0; stepIndex < steps.size(); stepIndex++)
                                <div class="${"timeline-item" + (stepIndex == 0 ? " is-first" : "") + (stepIndex == steps.size() - 1 ? " is-last" : "") + (stepIndex + 1 <= currentStepNumber ? " is-primary" : "")}">
                                    <div class="${"timeline-marker is-icon" + (stepIndex + 1 <= currentStepNumber ? " is-primary" : "")}">
                                        <span>${stepIndex + 1}</span>
                                    </div>
                                    <div class="timeline-content">
                                        <p class="${"is-size-6 " + (stepIndex + 1 == currentStepNumber ? "has-text-white has-text-weight-semibold" : "has-text-grey-light")}">
                                            ${String.valueOf(steps.get(stepIndex).get("title"))}@if(Boolean.TRUE.equals(steps.get(stepIndex).get("optional"))) <span class="is-size-7 has-text-grey ml-1">(optional)</span>@endif
                                        </p>
                                    </div>
                                </div>
                            @endfor
                        </div>
                    </aside>
                </div>
                <div class="column is-7">
                    <div class="box" data-theme="light">
                        @if("onboarding/steps/S1-welcome".equals(stepTemplate))
                            @template.onboarding.steps.S1_welcome(currentStepNumber = currentStepNumber, totalSteps = totalSteps, nextStepUrl = nextStepUrl)
                        @elseif("onboarding/steps/S2-provider".equals(stepTemplate))
 @template.onboarding.steps.S2_provider(currentStepNumber = currentStepNumber, totalSteps = totalSteps, previousStepUrl = previousStepUrl, error = error, providers = providers, selectedProvider = selectedProvider, csrf = csrf)
                        @elseif("onboarding/steps/S3-credentials".equals(stepTemplate))
 @template.onboarding.steps.S3_credentials(currentStepNumber = currentStepNumber, totalSteps = totalSteps, previousStepUrl = previousStepUrl, error = error, providerLabel = providerLabel, requiresApiKey = requiresApiKey, systemWideTokenName = systemWideTokenName, apiKey = apiKey, model = model, chatModelPropertyKey = chatModelPropertyKey, providerApiPropertyKey = providerApiPropertyKey, csrf = csrf)
                        @elseif("onboarding/steps/S4-agent".equals(stepTemplate))
 @template.onboarding.steps.S4_agent(currentStepNumber = currentStepNumber, totalSteps = totalSteps, previousStepUrl = previousStepUrl, error = error, agentContent = agentContent, csrf = csrf)
                        @elseif("onboarding/steps/S5-starters".equals(stepTemplate))
 @template.onboarding.steps.S5_starters(currentStepNumber = currentStepNumber, totalSteps = totalSteps, previousStepUrl = previousStepUrl, error = error, starterModules = starterModules, selectedStarterModuleId = selectedStarterModuleId, selectedStarterConfigured = selectedStarterConfigured, csrf = csrf)
                        @elseif("onboarding/steps/S6-complete".equals(stepTemplate))
                            @template.onboarding.steps.S6_complete(currentStepNumber = currentStepNumber, totalSteps = totalSteps, providerLabel = providerLabel)
                        @else
                            <article class="message is-danger">
                                <div class="message-body">Unknown onboarding step template: ${stepTemplate}</div>
                            </article>
                        @endif
                    </div>
                </div>
            </div>
        </div>
    </section>
</div>
<script src="https://cdn.jsdelivr.net/npm/htmx.org@2.0.8/dist/htmx.min.js" integrity="sha384-/TgkGk7p307TH7EXJDuUlgG3Ce1UVolAOFopFekQkkXihi5u/6OCvVKyz1W+idaz" crossorigin="anonymous"></script>
</body>
</html>
