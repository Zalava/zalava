@import java.util.List
@import org.zalava.web.ui.navigation.NavItem
@param List<NavItem> items
@param String activePage
@param String csrfName
@param String csrfValue
@param String accountName = ""

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
            @if(!accountName.isBlank())<p class="zalava-account-identity">Signed in as <strong>${accountName}</strong></p>@endif
            <form class="zalava-sign-out" method="post" action="/logout">
                <input type="hidden" name="${csrfName}" value="${csrfValue}">
                <button class="navbar-item zalava-sign-out-button" type="submit">Sign out</button>
            </form>
            </div>
        </div>
    </div>
</nav>
