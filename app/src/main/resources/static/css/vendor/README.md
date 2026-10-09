# Pinned UI assets

These are the same framework versions previously loaded from a CDN. Serving them
with the application keeps product styling and administrator forms available
without external network access. Preserve the accompanying upstream licenses.

| Library | Version | Upstream files |
| --- | --- | --- |
| Bulma | 1.0.4 | https://cdn.jsdelivr.net/npm/bulma@1.0.4/css/bulma.min.css |
| Bootstrap | 5.3.8 | https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css and https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/js/bootstrap.bundle.min.js |
| htmx | 2.0.4 | https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js |

When updating, replace the exact pinned assets and licenses together and run the
browser acceptance lane with external requests blocked. Local paths retain
versions for predictable browser cache invalidation.
