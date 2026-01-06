#!/usr/bin/env sh

##########################################################
# Gradle wrapper script for Unix/Linux/macOS systems
##########################################################

# Set JAVA_HOME if needed
# export JAVA_HOME=/path/to/java

DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/gradlew" "$@"