#!/usr/bin/env bash
# Checks the testing rules from CLAUDE.md ("Testing Best Practices") that a text
# search can catch. Runs as part of `./gradlew check` (task :app:checkTestRules).
set -euo pipefail
cd "$(dirname "$0")/.."

# Each module's main code, and the folders of tests: local and instrumented ones, and the
# fakes and helpers that tests in more than one module share (:testing). :shared's source sets
# are matched by name, so common code (commonMain, commonTest) is covered once it exists.
shopt -s nullglob
main_dirs=(app/src/main shared/src/*Main)
local_test_dirs=()
for dir in app/src/test shared/src/*HostTest shared/src/commonTest; do
    if [[ -d $dir ]]; then local_test_dirs+=("$dir"); fi
done
test_dirs=("${local_test_dirs[@]}" app/src/androidTest shared/src/*DeviceTest testing/src)

failed=0
fail() {
    echo "error: $*" >&2
    failed=1
}

# Rule: never skip tests to get them passing.
for file in $(grep -rlE --include='*.kt' '@([A-Za-z0-9_]+\.)*Ignore\b' "${test_dirs[@]}" 2>/dev/null || true); do
    fail "$file uses @Ignore. Fix the code, or explain why the expectation is wrong, instead of skipping the test."
done

# Rule: every class containing logic has a corresponding unit test.
while IFS= read -r source; do
    name=$(basename "$source" .kt)
    # A file that only declares an interface has no logic to test. A class or object
    # declaration may follow annotations (@Singleton) and modifiers (data, internal).
    if ! grep -qE '^[[:space:]]*(@[^[:space:]]+[[:space:]]+)*([a-z]+[[:space:]]+)*(class|object)[[:space:]]' "$source"; then
        continue
    fi
    if [[ -z $(find "${local_test_dirs[@]}" -name "${name}Test.kt") ]]; then
        fail "$source has no unit test. Add ${name}Test.kt to its module's local tests."
    fi
done < <(find "${main_dirs[@]}" \( -name '*ViewModel.kt' -o -name '*UseCase.kt' \
    -o -name '*Repository.kt' -o -name '*Mapper.kt' \))

# Rule: prefer fakes over mocks.
for file in $(grep -rliE 'mockk|mockito' --include='*.gradle.kts' --include='libs.versions.toml' \
    --exclude-dir=build --exclude-dir=.gradle . || true); do
    fail "$file adds a mocking library. Use a fake instead."
done

exit "$failed"
