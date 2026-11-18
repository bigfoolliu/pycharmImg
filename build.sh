#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

find_jdk_21() {
    if [[ -n "${JAVA_HOME:-}" ]]; then
        printf '%s\n' "$JAVA_HOME"
        return
    fi

    local candidate
    for candidate in /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21; do
        if [[ -x "$candidate/bin/javac" ]]; then
            printf '%s\n' "$candidate"
            return
        fi
    done

    if [[ -x /usr/libexec/java_home ]]; then
        /usr/libexec/java_home -v 21 2>/dev/null || true
    fi
}

JAVA_HOME="$(find_jdk_21)"
if [[ -z "$JAVA_HOME" || ! -x "$JAVA_HOME/bin/javac" ]]; then
    cat >&2 <<'EOF'
JDK 21 was not found.
Set JAVA_HOME to a JDK 21 installation, or install one with:
  brew install openjdk@21
EOF
    exit 1
fi

JAVAC_VERSION_OUTPUT="$("$JAVA_HOME/bin/javac" -version 2>&1)"
if [[ ! "$JAVAC_VERSION_OUTPUT" =~ ^javac[[:space:]]+21([.]|$) ]]; then
    printf 'Expected JDK 21, found: %s\n' "$JAVAC_VERSION_OUTPUT" >&2
    printf 'Set JAVA_HOME to a JDK 21 installation and retry.\n' >&2
    exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [[ ! -x "$PROJECT_DIR/gradlew" ]]; then
    printf 'Gradle Wrapper is missing or not executable: %s/gradlew\n' "$PROJECT_DIR" >&2
    exit 1
fi

cd "$PROJECT_DIR"
printf 'Building with %s\n' "$JAVAC_VERSION_OUTPUT"
if [[ -n "${PYCHARM_HOME:-}" ]]; then
    printf 'Using local PyCharm SDK: %s\n' "$PYCHARM_HOME"
else
    printf 'Gradle will use the configured PyCharm 2024.1.7 SDK; the first build may download it.\n'
fi

printf 'Building the plugin package and running Plugin Verifier against the configured PyCharm SDK.\n'
./gradlew buildPlugin verifyPlugin --no-daemon

printf '\nCompatibility check and build complete.\nPlugin ZIP files: %s/build/distributions\n' "$PROJECT_DIR"
printf 'Plugin Verifier reports: %s/build/reports/pluginVerifier\n' "$PROJECT_DIR"
