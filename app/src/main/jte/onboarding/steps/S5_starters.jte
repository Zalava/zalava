@import java.util.List
@import org.zalava.web.onboarding.steps.S5StarterModulesStep.StarterModule
@import org.springframework.security.web.csrf.CsrfToken

@param Integer currentStepNumber
@param Integer totalSteps
@param String previousStepUrl
@param String error
@param List<StarterModule> starterModules
@param String selectedStarterModuleId
@param Boolean selectedStarterConfigured
@param CsrfToken csrf

<div id="onboarding-step" class="step-fragment">
    <p class="is-uppercase has-text-weight-semibold has-text-grey is-size-7 mb-3">Step ${currentStepNumber} of ${totalSteps} · Optional</p>
    <h2 class="title is-3 mb-3">Choose a starter module</h2>
    <p class="subtitle is-5 has-text-grey-dark">SEA recommends these locally available modules. You can finish without one, or configure a selected module using the same safe setup flow available later in Modules.</p>
    @if(error != null)
        <article class="message is-danger"><div class="message-body">${error}</div></article>
    @endif
    @if(starterModules.isEmpty())
        <p class="notification is-light">No curated starter modules are available in this SEA installation. You can add modules later from Modules.</p>
    @else
        @for(StarterModule starter : starterModules)
            <section class="box mb-3" data-starter-module="${starter.moduleId()}">
                <h3 class="title is-5 mb-2">${starter.displayName()}</h3>
                <p class="has-text-grey mb-3">${starter.description()}</p>
                @if(starter.moduleId().equals(selectedStarterModuleId))
                    <span class="tag ${starter.configured() ? "is-success is-light" : "is-warning is-light"}">${starter.configured() ? "Configured" : "Configuration required"}</span>
                    @if(!starter.configured())
                        <a class="button is-primary is-small ml-2" href="/modules/${starter.moduleId()}/configuration?onboarding=true">Configure starter</a>
                    @endif
 <form class="is-inline ml-2" method="post" action="/onboarding/starters">@if(csrf != null)<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">@endif<input type="hidden" name="action" value="deselect"><button class="button is-light is-small" type="submit">Deselect</button></form>
                @else
 <form method="post" action="/onboarding/starters">@if(csrf != null)<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">@endif<input type="hidden" name="action" value="select"><input type="hidden" name="moduleId" value="${starter.moduleId()}"><button class="button is-light is-small" type="submit">Choose starter</button></form>
                @endif
            </section>
        @endfor
    @endif
    <div class="buttons mt-5">
        <a class="button is-light" href="${previousStepUrl}">Back</a>
 <form method="post" action="/onboarding/starters">@if(csrf != null)<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">@endif<input type="hidden" name="action" value="continue"><button class="button is-primary" type="submit">Continue</button></form>
    </div>
</div>
