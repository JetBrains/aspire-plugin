#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

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
