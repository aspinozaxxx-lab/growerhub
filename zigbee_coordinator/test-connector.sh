#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
platform=${CONNECTOR_PLATFORM:-linux/amd64}
docker build --platform "$platform" --target test -t growerhub-connector-test "$root/connector"
docker run --rm --network none --platform "$platform" growerhub-connector-test
docker build --platform "$platform" --target final -t growerhub-connector-release "$root/connector"
docker run --rm --network none --platform "$platform" growerhub-connector-release node --check connector.mjs
