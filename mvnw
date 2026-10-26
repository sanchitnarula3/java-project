#!/bin/sh
# Maven Wrapper script - downloads and runs Maven
# Licensed under the Apache License, Version 2.0

set -e

# Determine project base directory
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")" && pwd)}"
MAVEN_WRAPPERJAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

# Read distribution URL from properties
if [ -r "$MAVEN_WRAPPER_PROPERTIES" ]; then
    distributionUrl=$(grep 'distributionUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
    wrapperUrl=$(grep 'wrapperUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.9"

# Download Maven if not present
if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
    echo "Downloading Maven distribution..."
    mkdir -p "$MAVEN_HOME"
    TMPFILE=$(mktemp /tmp/maven-dist.XXXXXX.zip)
    if command -v curl > /dev/null 2>&1; then
        curl -fsSL -o "$TMPFILE" "$distributionUrl"
    elif command -v wget > /dev/null 2>&1; then
        wget -q -O "$TMPFILE" "$distributionUrl"
    else
        echo "Error: curl or wget required to download Maven"
        exit 1
    fi
    unzip -qo "$TMPFILE" -d "$HOME/.m2/wrapper/dists/"
    # The zip extracts to apache-maven-3.9.9/
    rm -f "$TMPFILE"
    echo "Maven downloaded to $MAVEN_HOME"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
