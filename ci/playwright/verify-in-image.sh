#!/usr/bin/env bash
set -euo pipefail

image=${1:?Usage: verify-in-image.sh <image-reference> [--lane all|host|oci-default|oci-full-jre] [gradle-options...]}
shift
lane=all
if [[ "${1:-}" == --lane ]]; then
    lane=${2:?--lane requires a value}
    shift 2
fi
# The union of CI lanes is the unchanged full local gate. Only the two OCI
# test tasks move out of host; their independent runners still execute them.
case "$lane" in
    all) tasks=(:module-api:test :module-api-test:check :app:check) ;;
    host) tasks=(:module-api:test :module-api-test:check :app:check
        --exclude-task :app:ociImageAcceptanceTest
        --exclude-task :app:ociFullJreImageAcceptanceTest) ;;
    oci-default) tasks=(:app:ociImageAcceptanceTest :app:ociComposeAcceptanceTest) ;;
    oci-full-jre) tasks=(:app:ociFullJreImageAcceptanceTest) ;;
    *) echo "Unknown verification lane: $lane" >&2; exit 1 ;;
esac
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
    ./gradlew "${tasks[@]}" --profile "$@"

docker run --rm --entrypoint bash "$image" -ec '
    if compgen -G "$PLAYWRIGHT_BROWSERS_PATH/firefox-*" > /dev/null ||
       compgen -G "$PLAYWRIGHT_BROWSERS_PATH/webkit-*" > /dev/null ||
       compgen -G "$PLAYWRIGHT_BROWSERS_PATH/chromium-*" > /dev/null; then
        echo "Headless acceptance image contains an unused browser" >&2
        exit 1
    fi
'
