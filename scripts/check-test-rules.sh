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
# (ARCHITECTURE.md, Testing approach). Each iosMain file needs a test in the same package of
# iosTest named after it, as CFStrings.kt has CFStringsTest and Lock.ios.kt has LockTest, or the
# one ios_tests names. A commonTest test doesn't count, since it may test only common code.
# Files are named from the app's package.
ios_tests=(
    data/Documents.ios.kt:FileCoordinatorDocumentsTest
    data/DocumentUri.ios.kt:FileUrlTest
    ui/OtherApps.ios.kt:IosOtherAppsTest
)
# These files have no test, for the reason above each, and leave the list when they get one.
ios_untested=(
    # Composables, which nothing tests on iOS until Compose UI tests run there (#355). A change to
    # one is checked on the simulator.
    ui/StringsLanguage.ios.kt
    ui/Toggles.ios.kt
    ui/badge/CompletionDate.ios.kt
    ui/data/ImportDialogs.ios.kt
    ui/navigation/BlueCardNavDisplay.ios.kt
    # MainViewController() shows the app's composables, as above, and IosModuleTest starts Koin
    # with initKoin(), as the app does.
    MainViewController.kt
    # Returns a constant.
    ui/KeepInViewWhileFocused.ios.kt
)
ios_main=shared/src/iosMain/kotlin/io/github/bryancassell/bluecard
ios_test=shared/src/iosTest/kotlin/io/github/bryancassell/bluecard

# Bash before 4.4, macOS's included, takes an empty array for an unset one, which set -u stops
# at, so a list is expanded only if it has entries: ${list[@]+"${list[@]}"}.
#
# The test this iOS file, named from the app's package, needs: its path, without ".kt".
ios_test_of() {
    local entry name
    for entry in ${ios_tests[@]+"${ios_tests[@]}"}; do
        if [[ ${entry%%:*} == "$1" ]]; then
            name=${entry#*:}
        fi
    done
    if [[ -z ${name-} ]]; then
        name=$(basename "$1" .kt)
        name=${name%.ios}Test
    fi
    if [[ $1 == */* ]]; then echo "$ios_test/${1%/*}/$name"; else echo "$ios_test/$name"; fi
}

# Whether this file exists with its name's case as given. macOS's file system ignores case and
# Linux's doesn't, so asking it would pass on a Mac and fail in CI.
exists_exactly() {
    local found
    for found in "${1%/*}"/*; do
        if [[ $found == "$1" ]]; then return 0; fi
    done
    return 1
}

is_ios_untested() {
    local listed
    for listed in ${ios_untested[@]+"${ios_untested[@]}"}; do
        if [[ $listed == "$1" ]]; then return 0; fi
    done
    return 1
}

if [[ ! -d $ios_main ]]; then
    fail "$ios_main isn't there, so the iOS test rule in $0 checks nothing. Point ios_main at the iOS code."
fi
while IFS= read -r source; do
    file=${source#"$ios_main"/}
    if is_ios_untested "$file"; then continue; fi
    test=$(ios_test_of "$file")
    if ! exists_exactly "$test.kt"; then
        fail "$source has no test. Add $test.kt, or name its test in ios_tests in $0, or list it in ios_untested there with the reason."
    fi
done < <(find shared/src/iosMain -name '*.kt')

# The lists stay current.
for entry in ${ios_tests[@]+"${ios_tests[@]}"}; do
    if [[ ! -f $ios_main/${entry%%:*} ]]; then
        fail "ios_tests in $0 names ${entry%%:*}, which isn't in $ios_main. Take it off the list."
    fi
done
for listed in ${ios_untested[@]+"${ios_untested[@]}"}; do
    if [[ ! -f $ios_main/$listed ]]; then
        fail "ios_untested in $0 lists $listed, which isn't in $ios_main. Take it off the list."
    elif exists_exactly "$(ios_test_of "$listed").kt"; then
        fail "ios_untested in $0 lists $listed, which has a test now. Take it off the list."
    fi
done

# Rule: prefer fakes over mocks.
for file in $(grep -rliE 'mockk|mockito' --include='*.gradle.kts' --include='libs.versions.toml' \
    --exclude-dir=build --exclude-dir=.gradle . || true); do
    fail "$file adds a mocking library. Use a fake instead."
done

exit "$failed"
