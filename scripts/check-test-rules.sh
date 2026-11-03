#!/usr/bin/env bash
# Checks the testing rules from CLAUDE.md ("Testing Best Practices") that a text
# search can catch. Runs as part of `./gradlew check` (task :app:checkTestRules).
set -euo pipefail
cd "$(dirname "$0")/.."

# Each module's main code, and the folders of tests: local and instrumented ones, and the
# fakes and helpers that tests in more than one module share (:testing). :shared's source sets
# are matched by name, so common code (commonMain, commonTest) is covered once it exists. iOS
# code's tests (iosTest) run on the simulator, which counts as local.
shopt -s nullglob
main_dirs=(app/src/main shared/src/*Main)
local_test_dirs=()
for dir in app/src/test shared/src/*HostTest shared/src/commonTest shared/src/iosTest; do
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

# Rule: code that only iOS runs has tests on the simulator, since nothing measures its coverage
# (ARCHITECTURE.md, Testing approach). Each iosMain file needs a test in iosTest or commonTest,
# named after the file or after a class, object or function it declares at the top level:
# CompletionDate.ios.kt's pickerLayoutDirection has PickerLayoutDirectionTest. These files have
# none, for the reason above each, and leave the list when they get one.
ios_untested=(
    # Composables, which nothing tests on iOS until Compose UI tests run there (#355). A change to
    # one is checked on the simulator.
    MainViewController.kt
    ui/StringsLanguage.ios.kt
    ui/data/ImportDialogs.ios.kt
    ui/navigation/BlueCardNavDisplay.ios.kt
    # Return a constant or their input.
    ui/KeepInViewWhileFocused.ios.kt
    di/UnformattedPhoneNumbers.kt
)
ios_main=shared/src/iosMain/kotlin/io/github/bryancassell/bluecard
ios_test_dirs=()
for dir in shared/src/iosTest shared/src/commonTest; do
    if [[ -d $dir ]]; then ios_test_dirs+=("$dir"); fi
done

# The names a test of this iOS file may have, without "Test.kt".
ios_test_names() {
    local name
    name=$(basename "$1" .kt)
    echo "${name%.ios}"
    grep -oE '^(@[^[:space:]]+[[:space:]]+)*([a-z]+[[:space:]]+)*(class|object)[[:space:]]+[A-Za-z0-9_]+' \
        "$1" | awk '{ print $NF }'
    # A function's name, capitalized.
    grep -oE '^(@[^[:space:]]+[[:space:]]+)*([a-z]+[[:space:]]+)*fun[[:space:]]+[A-Za-z0-9_]+\(' "$1" \
        | awk '{ name = substr($NF, 1, length($NF) - 1); print toupper(substr(name, 1, 1)) substr(name, 2) }'
}

has_ios_test() {
    local name
    for name in $(ios_test_names "$1"); do
        if [[ -n $(find "${ios_test_dirs[@]}" -name "${name}Test.kt") ]]; then return 0; fi
    done
    return 1
}

is_ios_untested() {
    local listed
    for listed in "${ios_untested[@]}"; do
        if [[ $listed == "$1" ]]; then return 0; fi
    done
    return 1
}

if [[ -d $ios_main ]]; then
    while IFS= read -r source; do
        if is_ios_untested "${source#"$ios_main"/}"; then continue; fi
        if ! has_ios_test "$source"; then
            fail "$source has no test. Add one to shared/src/iosTest named after the file or after a class, object or function it declares, or list it in ios_untested in $0 with the reason."
        fi
    done < <(find "$ios_main" -name '*.kt')

    # The list stays current.
    for listed in "${ios_untested[@]}"; do
        if [[ ! -f $ios_main/$listed ]]; then
            fail "ios_untested in $0 lists $listed, which isn't in $ios_main. Take it off the list."
        elif has_ios_test "$ios_main/$listed"; then
            fail "ios_untested in $0 lists $listed, which has a test now. Take it off the list."
        fi
    done
fi

# Rule: prefer fakes over mocks.
for file in $(grep -rliE 'mockk|mockito' --include='*.gradle.kts' --include='libs.versions.toml' \
    --exclude-dir=build --exclude-dir=.gradle . || true); do
    fail "$file adds a mocking library. Use a fake instead."
done

exit "$failed"
