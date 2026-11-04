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
# that only iOS's press feedback replaces, so only ui/Buttons.kt uses them.
buttons='Button|ElevatedButton|FilledTonalButton|OutlinedButton|TextButton'
buttons+='|IconButton|FilledIconButton|FilledTonalIconButton|OutlinedIconButton'
buttons+='|IconToggleButton|FilledIconToggleButton|FilledTonalIconToggleButton'
buttons+='|OutlinedIconToggleButton|FloatingActionButton|SmallFloatingActionButton'
buttons+='|LargeFloatingActionButton|ExtendedFloatingActionButton'
while IFS= read -r file; do
    if [[ $file == */bluecard/ui/Buttons.kt ]]; then continue; fi
    fail "$file uses Material's buttons. Use the app's own from ui/Buttons.kt, which give iOS its press feedback."
done < <(grep -rlE --include='*.kt' "^import androidx\.compose\.material3\.($buttons|\*)$" \
    "${main_dirs[@]}" 2>/dev/null || true)

exit $failed
