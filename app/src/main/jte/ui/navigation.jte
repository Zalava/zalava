@import org.zalava.ui.navigation.NavItem
@param java.util.List<NavItem> items
@param String activePage

<nav class="navbar sea-navbar" role="navigation" aria-label="SEA navigation">
    <div class="container">
        <div class="navbar-brand">
            <a class="navbar-item sea-brand" href="/dashboard" aria-label="SEA Dashboard">
                <span class="sea-brand-mark">S</span>
                <span>SEA</span>
            </a>
            <button class="navbar-burger" type="button" aria-label="Open navigation" aria-expanded="false"
                    data-target="sea-product-navigation">
                <span aria-hidden="true"></span>
                <span aria-hidden="true"></span>
                <span aria-hidden="true"></span>
                <span aria-hidden="true"></span>
            </button>
        </div>

        <div id="sea-product-navigation" class="navbar-menu">
            <div class="navbar-start">
                @for(NavItem item : items)
                    @if(item.id().equals(activePage))
                        <a class="navbar-item is-active" aria-current="page" href="${item.href()}">${item.label()}</a>
                    @else
                        <a class="navbar-item" href="${item.href()}">${item.label()}</a>
                    @endif
                @endfor
            </div>
        </div>
    </div>
</nav>

<script>
document.addEventListener('DOMContentLoaded', function () {
    var burger = document.querySelector('.navbar-burger');
    var menu = document.getElementById('sea-product-navigation');
    if (!burger || !menu) {
        return;
    }
    burger.addEventListener('click', function () {
        var active = burger.classList.toggle('is-active');
        menu.classList.toggle('is-active', active);
        burger.setAttribute('aria-expanded', String(active));
    });
});
</script>
