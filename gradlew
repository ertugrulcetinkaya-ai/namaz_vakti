#!/usr/bin/env sh

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if [ -x "${JAVA_HOME}/bin/java" ]; then
  JAVA_CMD="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_CMD="java"
else
  echo "Java is required to run Gradle." >&2
  exit 1
fi

exec "$JAVA_CMD" -classpath "$DIR/gradle/wrapper/*" org.gradle.wrapper.GradleWrapperMain "$@"
