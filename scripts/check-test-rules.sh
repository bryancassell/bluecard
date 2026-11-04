#!/usr/bin/env bash
# Checks the testing rules from CLAUDE.md ("Testing Best Practices") that a text
# search can catch. Runs as part of `./gradlew check` (task :app:checkTestRules).
set -euo pipefail
cd "$(dirname "$0")/.."

# Each module's main code, and the folders of tests: local and instrumented ones, and the
# fakes and helpers that tests in more than one module share (:testing). :shared's source sets
# are matched by name, so common code (commonMain, commonTest) is covered once it exists. The
# tests that run on the iOS simulator, iOS code's (iosTest) and common ones, count as local.
shopt -s nullglob
main_dirs=(app/src/main shared/src/*Main)
ios_test_dirs=(shared/src/iosTest shared/src/commonTest)
local_test_dirs=()
for dir in app/src/test shared/src/*HostTest "${ios_test_dirs[@]}"; do
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
# (ARCHITECTURE.md, Testing approach). Each iosMain file needs a test in the same package of
# iosTest or commonTest, named after the file or after a class, object or function it declares at
# the top level and doesn't make private: CompletionDate.ios.kt's pickerLayoutDirection has
# PickerLayoutDirectionTest. These files, named from the app's package, have none, for the reason
# above each, and leave the list when they get one.
ios_untested=(
    # Composables, which nothing tests on iOS until Compose UI tests run there (#355). A change to
    # one is checked on the simulator.
    ui/StringsLanguage.ios.kt
    ui/data/ImportDialogs.ios.kt
    ui/navigation/BlueCardNavDisplay.ios.kt
    # MainViewController() shows the app's composables, as above, and IosModuleTest starts Koin
    # with initKoin(), as the app does.
    MainViewController.kt
    # Return a constant or their input.
    ui/KeepInViewWhileFocused.ios.kt
    di/UnformattedPhoneNumbers.kt
)
ios_sources=shared/src/iosMain/kotlin
app_package=io/github/bryancassell/bluecard

# The names a test of this iOS file may have, without "Test.kt": the file's, and those of the
# classes, objects and functions it declares at the top level, apart from private ones. A
# function's name is capitalized, and read after its type parameters and receiver, as in
# `fun <T> List<T>.name(`.
ios_test_names() {
    local name
    name=$(basename "$1" .kt)
    echo "${name%.ios}"
    awk '{
        line = $0
        while (match(line, /^@[A-Za-z.]+(\([^)]*\))?[ \t]+/)) line = substr(line, RLENGTH + 1)
        if (!match(line, /^([a-z]+[ \t]+)*(class|object|fun)[ \t]+/)) next
        modifiers = substr(line, 1, RLENGTH)
        line = substr(line, RLENGTH + 1)
        if (modifiers ~ /(^|[ \t])private[ \t]/) next
        if (modifiers ~ /fun[ \t]+$/) {
            sub(/^<[^>]*>[ \t]*/, "", line)
            if (!match(line, /^[^(=:]*\(/)) next
            n = split(substr(line, 1, RLENGTH - 1), parts, ".")
            print toupper(substr(parts[n], 1, 1)) substr(parts[n], 2)
        } else if (match(line, /^[A-Za-z0-9_]+/)) {
            print substr(line, 1, RLENGTH)
        }
    }' "$1"
}

# Whether this iOS file, given from $ios_sources, has a test in the same package.
has_ios_test() {
    local package name dir
    package=$(dirname "$1")
    for name in $(ios_test_names "$ios_sources/$1"); do
        for dir in "${ios_test_dirs[@]}"; do
            if [[ -f $dir/kotlin/$package/${name}Test.kt ]]; then return 0; fi
        done
    done
    return 1
}

# Bash before 4.4, macOS's included, takes an empty array for an unset one, which set -u stops
# at, so the list is expanded only if it has entries.
is_ios_untested() {
    local listed
    for listed in ${ios_untested[@]+"${ios_untested[@]}"}; do
        if [[ $listed == "$1" ]]; then return 0; fi
    done
    return 1
}

while IFS= read -r source; do
    file=${source#"$ios_sources"/}
    if is_ios_untested "${file#"$app_package"/}"; then continue; fi
    if ! has_ios_test "$file"; then
        fail "$source has no test. Add one to iosTest, in the same package, named after the file or after a class, object or function it declares, or list it in ios_untested in $0 with the reason."
    fi
done < <(find shared/src/iosMain -name '*.kt')

# The list stays current.
for listed in ${ios_untested[@]+"${ios_untested[@]}"}; do
    if [[ ! -f $ios_sources/$app_package/$listed ]]; then
        fail "ios_untested in $0 lists $listed, which isn't in $ios_sources/$app_package. Take it off the list."
    elif has_ios_test "$app_package/$listed"; then
        fail "ios_untested in $0 lists $listed, which has a test now. Take it off the list."
    fi
done

# Rule: prefer fakes over mocks.
for file in $(grep -rliE 'mockk|mockito' --include='*.gradle.kts' --include='libs.versions.toml' \
    --exclude-dir=build --exclude-dir=.gradle . || true); do
    fail "$file adds a mocking library. Use a fake instead."
done

exit "$failed"
