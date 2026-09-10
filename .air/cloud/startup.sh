#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

configure_java_proxy() {
  local proxy_url proxy_host_port proxy_host proxy_port env_file profile marker
  proxy_url="${HTTPS_PROXY:-${https_proxy:-}}"
  if [ -z "$proxy_url" ]; then
    return
  fi

  proxy_host_port="${proxy_url#*://}"
  proxy_host_port="${proxy_host_port##*@}"
  proxy_host="${proxy_host_port%%:*}"
  proxy_port="${proxy_host_port##*:}"
  env_file="$HOME/.air-java-proxy.sh"
  marker='# Air Java proxy configuration'

  printf '%s\nexport JAVA_TOOL_OPTIONS=%q\n' "$marker" \
    "-Dhttp.proxyHost=$proxy_host -Dhttp.proxyPort=$proxy_port -Dhttps.proxyHost=$proxy_host -Dhttps.proxyPort=$proxy_port -Dhttp.nonProxyHosts=localhost|127.0.0.1" > "$env_file"

  profile=
  for profile in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
    if [ -f "$profile" ]; then
      break
    fi
  done
  if [ ! -f "$profile" ]; then
    profile="$HOME/.profile"
    touch "$profile"
  fi
  for profile in "$profile" "$HOME/.bashrc"; do
    touch "$profile"
    grep -Fq "$marker" "$profile" || printf '\n%s\n. "$HOME/.air-java-proxy.sh"\n' "$marker" >> "$profile"
  done

  # shellcheck disable=SC1090
  . "$env_file"
}

configure_java_proxy

if [ "${AIR_STARTUP_MODE:-}" = warmup ]; then
  WARMUP=1
else
  WARMUP=
fi

healthcheck() {
  echo "Checking the prepared Aspire plugin development environment..."
  java -version
  ./dotnet.cmd --version | grep -Fx '10.0.301'
  test -s build/DotNetSdkPath.Generated.props
  test -s nuget.config
  test -n "$(find build/distributions -maxdepth 1 -type f -name 'aspire-plugin-*.zip' -print -quit)"
  echo "Aspire plugin build artifacts and both pinned toolchains are ready."
}

echo "Preparing generated RD protocol and .NET SDK files..."
./gradlew --no-daemon prepareDotNetPart

if [ -n "${WARMUP:-}" ]; then
  echo "Warming Gradle, Rider SDK, NuGet, .NET SDK, and plugin build caches..."
  ./gradlew --no-daemon buildPlugin
  healthcheck
fi
