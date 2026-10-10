@import java.util.List
@import org.zalava.web.ui.navigation.NavItem
@param List<NavItem> items
@param String activePage
@param String csrfName
@param String csrfValue

<nav class="navbar zalava-navbar" role="navigation" aria-label="Zalava navigation">
    <div class="container">
        <div class="navbar-brand">
            <a class="navbar-item zalava-brand" href="/dashboard" aria-label="Zalava Dashboard">
                <span class="zalava-brand-mark">Z</span>
                <span>Zalava</span>
            </a>
            <button class="navbar-burger" type="button" aria-label="Open navigation" aria-expanded="false"
                    aria-controls="zalava-product-navigation" data-target="zalava-product-navigation">
                <span aria-hidden="true"></span>
                <span aria-hidden="true"></span>
                <span aria-hidden="true"></span>
                <span aria-hidden="true"></span>
            </button>
        </div>

        <div id="zalava-product-navigation" class="navbar-menu" aria-label="Product destinations">
            <button class="zalava-navigation-close" type="button" aria-label="Close navigation">Close</button>
            <div class="navbar-start">
                @for(NavItem item : items)
                    @if(item.id().equals(activePage))
                    <a class="navbar-item is-active" aria-current="page" href="${item.href()}" title="${item.label()}" data-nav-initial="${item.label().substring(0, 1)}"><span class="zalava-nav-label">${item.label()}</span></a>
                    @else
                    <a class="navbar-item" href="${item.href()}" title="${item.label()}" data-nav-initial="${item.label().substring(0, 1)}"><span class="zalava-nav-label">${item.label()}</span></a>
                    @endif
                @endfor
            <form class="zalava-sign-out" method="post" action="/logout">
                <input type="hidden" name="${csrfName}" value="${csrfValue}">
                <button class="navbar-item zalava-sign-out-button" type="submit">Sign out</button>
            </form>
            </div>
        </div>
    </div>
</nav>

<script>
document.addEventListener('DOMContentLoaded', function () {
    var burger = document.querySelector('.navbar-burger');
        var menu = document.getElementById('zalava-product-navigation');
        var closeButton = menu && menu.querySelector('.zalava-navigation-close');
        var previousFocus;
        if (!burger || !menu || !closeButton) {
            return;
        }
        function closeNavigation() {
            menu.classList.remove('is-active');
            burger.classList.remove('is-active');
            burger.setAttribute('aria-expanded', 'false');
            if (previousFocus) previousFocus.focus();
        }
        function openNavigation() {
            previousFocus = document.activeElement;
            menu.classList.add('is-active');
            burger.classList.add('is-active');
            burger.setAttribute('aria-expanded', 'true');
            closeButton.focus();
        }
        burger.addEventListener('click', function () {
            if (menu.classList.contains('is-active')) closeNavigation();
            else openNavigation();
        });
        closeButton.addEventListener('click', closeNavigation);
        menu.querySelectorAll('a').forEach(function (link) { link.addEventListener('click', closeNavigation); });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && menu.classList.contains('is-active')) closeNavigation();
        });
        document.querySelectorAll('form:not([data-repeatable])').forEach(function (form) {
            form.addEventListener('submit', function () {
                if (!form.checkValidity()) return;
                form.querySelectorAll('button[type="submit"]').forEach(function (button) {
                    button.disabled = true;
                    button.setAttribute('aria-disabled', 'true');
                    button.dataset.pending = 'true';
                    button.dataset.pendingLabel = button.textContent.trim();
                    button.textContent = 'Working…';
                });
                var status = document.createElement('p');
                status.className = 'zalava-action-status';
                status.setAttribute('role', 'status');
                status.textContent = 'Zalava is working on your request.';
                form.appendChild(status);
            }, { once: true });
        });
});
</script>
