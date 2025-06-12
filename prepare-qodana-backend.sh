#! /bin/sh
set -eu

./gradlew prepareDotNetPart
dotnet build ./aspire-plugin.sln
