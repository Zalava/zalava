FROM node:22-bookworm-slim@sha256:43ac6c60b8f89723f746e8a92ce91abd5017e627ce1ddfe4238355d3a30b772c AS node
FROM eclipse-temurin:25-jdk-noble@sha256:589ff4cc3f71aab462e7048a47a0d10edf57fbccde3fceea2281e610bf5880b4

COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s ../lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx

# Supplied from the Java dependency in build.gradle by the image workflow.
ARG PLAYWRIGHT_VERSION
ENV ZALAVA_CI_PLAYWRIGHT_VERSION=${PLAYWRIGHT_VERSION} \
    PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
RUN test -n "$PLAYWRIGHT_VERSION" \
    && apt-get update \
    && apt-get install -y --no-install-recommends git unzip python3 \
    && npx --yes "playwright@${PLAYWRIGHT_VERSION}" install --with-deps --only-shell chromium \
    && chmod -R a+rX /ms-playwright \
    && rm -rf /root/.npm /var/lib/apt/lists/*
