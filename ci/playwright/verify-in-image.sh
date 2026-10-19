#!/usr/bin/env bash
set -euo pipefail

image=${1:?Usage: verify-in-image.sh <image-reference> [gradle-options...]}
repository=$(pwd -P)
docker_cli=$(command -v docker)
docker compose version >/dev/null
compose_cli=$(docker info --format '{{range .ClientInfo.Plugins}}{{if eq .Name "compose"}}{{.Path}}{{end}}{{end}}')
[[ -f "$compose_cli" ]] || { echo "Docker Compose plugin path is unavailable" >&2; exit 1; }
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
    --volume "$docker_cli:/usr/local/bin/docker:ro" \
    --volume "$compose_cli:/usr/local/lib/docker/cli-plugins/docker-compose:ro" \
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
