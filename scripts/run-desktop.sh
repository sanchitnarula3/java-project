#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

(cd "$PROJECT_DIR" && ./mvnw clean -DskipTests package)
(cd "$PROJECT_DIR/desktop" && ../mvnw -DskipTests package javafx:run)
