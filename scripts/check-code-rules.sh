#!/usr/bin/env bash
# Checks conventions from ARCHITECTURE.md that a text search can catch. Runs as part of
# `./gradlew check` (task :app:checkCodeRules). Testing rules are in check-test-rules.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

shopt -s nullglob
main_dirs=(app/src/main shared/src/*Main)

failed=0
fail() {
    echo "error: $*" >&2
    failed=1
}

# Rule: buttons are the app's own (ARCHITECTURE.md, Theme). Material's buttons draw a ripple
# that only iOS's press feedback replaces, so only ui/Buttons.kt uses them. Matched anywhere in
# a line, so an import under another name, or a call by its full name, counts too.
buttons_file=shared/src/commonMain/kotlin/io/github/bryancassell/bluecard/ui/Buttons.kt
buttons='Button|ElevatedButton|FilledTonalButton|OutlinedButton|TextButton'
buttons+='|IconButton|FilledIconButton|FilledTonalIconButton|OutlinedIconButton'
buttons+='|IconToggleButton|FilledIconToggleButton|FilledTonalIconToggleButton'
buttons+='|OutlinedIconToggleButton|FloatingActionButton|SmallFloatingActionButton'
buttons+='|LargeFloatingActionButton|ExtendedFloatingActionButton'
for dir in "${main_dirs[@]}"; do
    if [[ ! -d $dir ]]; then fail "$dir is gone. Update main_dirs in $0."; fi
done
if [[ ! -f $buttons_file ]]; then fail "$buttons_file is gone. Update buttons_file in $0."; fi
status=0
files=$(grep -rlE --include='*.kt' "androidx\.compose\.material3\.(($buttons)\b|\*)" \
    "${main_dirs[@]}") || status=$?
if [[ $status -gt 1 ]]; then fail "grep failed while looking for Material's buttons."; fi
for file in $files; do
    if [[ $file == "$buttons_file" ]]; then continue; fi
    fail "$file uses Material's buttons. Use the app's own from ui/Buttons.kt, which give iOS its press feedback."
done

exit $failed
