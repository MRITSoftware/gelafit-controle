#!/bin/sh

set -e

PRG="$0"
while [ -h "$PRG" ]; do
    ls_output=$(ls -ld "$PRG")
    link=$(expr "$ls_output" : '.*-> \(.*\)$')
    case "$link" in
        /*) PRG="$link" ;;
        *) PRG=$(dirname "$PRG")"/$link" ;;
    esac
done

SAVED_PWD=$(pwd)
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME=$(pwd -P)
cd "$SAVED_PWD" >/dev/null

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ]; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD="java"
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
