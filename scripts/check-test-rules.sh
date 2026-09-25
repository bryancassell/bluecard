#!/usr/bin/env bash
# Checks the testing rules from CLAUDE.md ("Testing Best Practices") that a text
# search can catch. Runs as part of `./gradlew check` (task :app:checkTestRules).
set -euo pipefail
cd "$(dirname "$0")/.."

failed=0
fail() {
    echo "error: $*" >&2
    failed=1
}

# Rule: never skip tests to get them passing.
for file in $(grep -rlE --include='*.kt' '@([A-Za-z0-9_]+\.)*Ignore\b' app/src/test app/src/androidTest 2>/dev/null || true); do
    fail "$file uses @Ignore. Fix the code, or explain why the expectation is wrong, instead of skipping the test."
done

# Rule: every class containing logic has a corresponding unit test.
while IFS= read -r source; do
    name=$(basename "$source" .kt)
    if [[ -z $(find app/src/test -name "${name}Test.kt") ]]; then
        fail "$source has no unit test. Add app/src/test/.../${name}Test.kt."
    fi
done < <(find app/src/main \( -name '*ViewModel.kt' -o -name '*UseCase.kt' \
    -o -name '*Repository.kt' -o -name '*Mapper.kt' \))

# Rule: prefer fakes over mocks.
for file in $(grep -rliE 'mockk|mockito' --include='*.gradle.kts' --include='libs.versions.toml' \
    --exclude-dir=build --exclude-dir=.gradle . || true); do
    fail "$file adds a mocking library. Use a fake instead."
done

exit "$failed"
