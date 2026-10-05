#!/usr/bin/env bash
set -euo pipefail

image=${1:?Usage: verify-in-image.sh <image-reference> [gradle-options...]}
repository=$(pwd -P)
gradle_home=${GRADLE_USER_HOME:-"$HOME/.gradle"}
mkdir -p "$gradle_home"
mkdir -p "$HOME/.npm"
gradle_home=$(cd "$gradle_home" && pwd -P)

# Identical paths and the host network preserve Testcontainers bind mounts,
# mapped database ports and the packaged host's localhost browser journeys.
docker run --rm --network host \
    --user "$(id -u):$(id -g)" \
    --group-add "$(stat -c '%g' /var/run/docker.sock)" \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    --volume "$repository:$repository" \
    --volume "$gradle_home:$gradle_home" \
    --volume "$HOME/.npm:$HOME/.npm" \
    --workdir "$repository" \
    --env "HOME=$HOME" \
    --env "GRADLE_USER_HOME=$gradle_home" \
    --env CI --env GITHUB_ACTIONS \
    "$image" \
    ./gradlew :module-api:test :module-api-test:check :app:check --profile "${@:2}"

docker run --rm --entrypoint bash "$image" -ec '
    if compgen -G "$PLAYWRIGHT_BROWSERS_PATH/firefox-*" > /dev/null ||
       compgen -G "$PLAYWRIGHT_BROWSERS_PATH/webkit-*" > /dev/null ||
       compgen -G "$PLAYWRIGHT_BROWSERS_PATH/chromium-*" > /dev/null; then
        echo "Headless acceptance image contains an unused browser" >&2
        exit 1
    fi
'
