@import java.util.List
@import org.zalava.SupportedProvider
@import org.springframework.security.web.csrf.CsrfToken

@param Integer currentStepNumber
@param Integer totalSteps
@param String previousStepUrl
@param String error
@param List<SupportedProvider> providers
@param String selectedProvider
@param CsrfToken csrf

<div id="onboarding-step" class="step-fragment">
    <p class="is-uppercase has-text-weight-semibold has-text-grey is-size-7 mb-3">Step ${currentStepNumber} of ${totalSteps}</p>
    <h2 class="title is-3 mb-3">Choose a model provider.</h2>
    <p class="subtitle is-5 has-text-grey-dark">Pick the runtime you want this installation to use first. You can change it later in configuration if needed.</p>

    @if(error != null)
    <article class="message is-danger">
        <div class="message-body">${error}</div>
    </article>
    @endif

    <form method="post" action="/onboarding/provider">
        @if(csrf != null)<input type="hidden" name="${csrf.getParameterName()}" value="${csrf.getToken()}">@endif
        <div class="fixed-grid has-1-cols has-1-cols-mobile has-3-cols-tablet mb-5">
            <div class="grid">
                @for(var provider : providers)
                <div class="cell">
                    <label class="${provider.id().equals(selectedProvider) ? "provider-card is-selected" : "provider-card"}">
                        <input type="radio" name="provider" value="${provider.id()}" checked="${provider.id().equals(selectedProvider)}">
                        <p class="title is-5 mt-3 mb-2">${provider.label()}</p>
                        <p class="has-text-grey">${provider.slogan()}.</p>
                    </label>
                </div>
                @endfor
            </div>
        </div>

        <div class="buttons mt-4">
            <a class="button is-light is-medium" href="${previousStepUrl}">Back</a>
            <button class="button is-primary is-medium" type="submit">Continue</button>
            <span class="button is-ghost is-medium htmx-indicator">Saving...</span>
        </div>
    </form>
</div>
