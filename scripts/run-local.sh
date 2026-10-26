#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOMCAT_HOME="${CATALINA_HOME:-/private/tmp/omr-tomcat/apache-tomcat-10.1.61}"
WAR="$PROJECT_DIR/target/omr-mcq-system.war"

if [[ ! -x "$TOMCAT_HOME/bin/catalina.sh" ]]; then
  echo "Tomcat not found. Set CATALINA_HOME to your Tomcat 10.1 installation." >&2
  exit 1
fi
if [[ ! -f "$WAR" ]]; then
  (cd "$PROJECT_DIR" && ./mvnw -DskipTests package)
fi

export CATALINA_HOME="$TOMCAT_HOME"
unset DB_URL DB_USER DB_PASSWORD TIDB_HOST TIDB_PORT TIDB_DATABASE TIDB_DB_NAME TIDB_USER TIDB_PASSWORD

mkdir -p "$TOMCAT_HOME/webapps"
cp "$WAR" "$TOMCAT_HOME/webapps/omr-mcq-system.war"
"$TOMCAT_HOME/bin/catalina.sh" stop 10 >/dev/null 2>&1 || true
sleep 2
exec "$TOMCAT_HOME/bin/catalina.sh" run
