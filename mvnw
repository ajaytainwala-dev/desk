#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper - a shell script to run Maven without requiring a local
# Maven installation. It downloads and runs the Maven distribution declared
# in .mvn/maven-wrapper.properties.
# ----------------------------------------------------------------------------

set -eu

# Resolve the directory of this script.
DIRNAME="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$DIRNAME/.mvn/maven-wrapper.jar"
PROPERTIES_FILE="$DIRNAME/.mvn/maven-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Error: Maven Wrapper jar not found at $WRAPPER_JAR" >&2
    exit 1
fi

if [ ! -f "$PROPERTIES_FILE" ]; then
    echo "Error: Maven Wrapper properties not found at $PROPERTIES_FILE" >&2
    exit 1
fi

# The maven-wrapper.jar does not declare a Main-Class in its manifest on all
# builds, so invoke the entry point explicitly and pass the project directory.
exec java ${MAVEN_OPTS:-} \
    -Dmaven.multiModuleProjectDirectory="$DIRNAME" \
    -cp "$WRAPPER_JAR" \
    org.apache.maven.wrapper.MavenWrapperMain "$@"
