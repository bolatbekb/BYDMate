#!/usr/bin/env bash
# skin imports: restores and checks the fork's skin hooks in AUTHOR files, from one list.
#
# The skin reaches author screens through one-line hooks, each marked `// fork: skin…`:
#   [imports]     `import androidx.compose.material3.{Switch,Card,Slider,HorizontalDivider,AlertDialog}`
#                 and `import androidx.compose.foundation.shape.RoundedCornerShape` become
#                 `import com.bydmate.app.ui.skin.<Name> // fork: skin`. The call sites stay the
#                 author's: the fork functions have the same names, parameters and defaults.
#                 Only the names a file actually imports are touched.
#   [background]  `.background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))` becomes
#                 `.skinBackground() // fork: skin background`, and the file gets
#                 `import com.bydmate.app.ui.skin.skinBackground // fork: skin`: it takes the place
#                 of `import androidx.compose.ui.graphics.Brush` when nothing else in the file uses
#                 Brush (so the hunk stays marked), otherwise it goes after the last import.
#
# tools/skin-imports.list names the files (paths from the repo root) under `[imports]` and
# `[background]`; a file may sit in both; `#` starts a note. SkinImportsTest reads the same list.
#
# usage: tools/skin-imports.sh --apply   rewrite the listed files; idempotent (a second run changes nothing)
#        tools/skin-imports.sh --check   exit 1 and list every listed file that still has a raw import or
#                                        the raw background (or lost a hook); exit 0 prints OK
# After an upstream merge: tools/skin-imports.sh --apply && tools/skin-imports.sh --check, then
# tools/fork-marks.sh and a build.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
list="$root/tools/skin-imports.list"
mode="${1:-}"
case "$mode" in
  --apply|--check) ;;
  *) echo "usage: tools/skin-imports.sh --apply|--check" >&2; exit 2 ;;
esac
[ -f "$list" ] || { echo "skin imports: no list at $list" >&2; exit 2; }

M3_NAMES="Switch Card Slider HorizontalDivider AlertDialog"

# entries <section>: the paths listed under [section]
entries() {
  awk -v want="[$1]" '
    { sub(/#.*/, ""); gsub(/^[ \t]+|[ \t]+$/, "") }
    $0 == "" { next }
    /^\[.*\]$/ { sec = $0; next }
    sec == want { print }
  ' "$list"
}

# rewrite <file> <imports 0|1> <background 0|1>: prints the rewritten file on stdout
rewrite() {
  awk -v names="$M3_NAMES" -v doImports="$2" -v doBg="$3" '
    { line[++n] = $0 }
    END {
      raw = ".background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))"
      hook = ".skinBackground()"
      skinImport = "import com.bydmate.app.ui.skin.skinBackground // fork: skin"
      cnt = split(names, nm, " ")
      hasHook = 0; hasImport = 0; brushAt = 0; brushUsed = 0; lastImport = 0; pkg = 0
      for (i = 1; i <= n; i++) {
        s = line[i]
        if (s ~ /^package /) pkg = i
        if (s ~ /^import /) {
          lastImport = i
          if (doImports) {
            for (k = 1; k <= cnt; k++)
              if (s ~ ("^import androidx\\.compose\\.material3\\." nm[k] "[ \t]*$"))
                s = "import com.bydmate.app.ui.skin." nm[k] " // fork: skin"
            if (s ~ /^import androidx\.compose\.foundation\.shape\.RoundedCornerShape[ \t]*$/)
              s = "import com.bydmate.app.ui.skin.RoundedCornerShape // fork: skin"
          }
          if (s == skinImport) hasImport = 1
          if (s ~ /^import androidx\.compose\.ui\.graphics\.Brush[ \t]*$/) brushAt = i
        } else if (doBg) {
          changed = 0
          while ((p = index(s, raw)) > 0) {
            s = substr(s, 1, p - 1) hook substr(s, p + length(raw)); changed = 1
          }
          if (changed && tolower(s) !~ /fork/) { sub(/[ \t]+$/, "", s); s = s " // fork: skin background" }
          if (index(s, hook) > 0) hasHook = 1
          if (s ~ /(^|[^A-Za-z0-9_])Brush([^A-Za-z0-9_]|$)/) brushUsed = 1
        }
        out[i] = s
      }
      insertAfter = 0
      if (doBg && hasHook && !hasImport) {
        if (brushAt && !brushUsed) out[brushAt] = skinImport
        else insertAfter = (lastImport ? lastImport : pkg)
      }
      for (i = 1; i <= n; i++) {
        print out[i]
        if (i == insertAfter) print skinImport
      }
    }
  ' "$1"
}

# problems <file> <imports 0|1> <background 0|1>: one line per problem
problems() {
  awk -v f="$1" -v names="$M3_NAMES" -v doImports="$2" -v doBg="$3" '
    BEGIN { cnt = split(names, nm, " ") }
    {
      if (doImports && $0 ~ /^import /) {
        for (k = 1; k <= cnt; k++)
          if ($0 ~ ("^import androidx\\.compose\\.material3\\." nm[k] "[ \t]*$")) printf "%s:%d  raw import of %s\n", f, NR, nm[k]
        if ($0 ~ /^import androidx\.compose\.foundation\.shape\.RoundedCornerShape[ \t]*$/)
          printf "%s:%d  raw import of RoundedCornerShape\n", f, NR
        if ($0 ~ /^import com\.bydmate\.app\.ui\.skin\.(Switch|Card|Slider|HorizontalDivider|AlertDialog|RoundedCornerShape) \/\/ fork: skin$/) skinImports++
      }
      if (doBg) {
        if (index($0, "background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))") > 0)
          printf "%s:%d  raw background gradient\n", f, NR
        if (index($0, ".skinBackground() // fork: skin background") > 0) hooks++
        if ($0 == "import com.bydmate.app.ui.skin.skinBackground // fork: skin") bgImport++
      }
    }
    END {
      if (doImports && !skinImports) printf "%s  no skin import at all (drop it from [imports]?)\n", f
      if (doBg && !hooks) printf "%s  no skinBackground hook\n", f
      if (doBg && hooks && !bgImport) printf "%s  skinBackground without its import\n", f
    }
  ' "$root/$1"
}

mapfile -t imports < <(entries imports)
mapfile -t backgrounds < <(entries background)
all=$(printf '%s\n' "${imports[@]}" "${backgrounds[@]}" | awk 'NF && !seen[$0]++')

bad=0; changed=0
while IFS= read -r f; do
  [ -n "$f" ] || continue
  imp=0; bg=0
  printf '%s\n' "${imports[@]}" | grep -qxF -- "$f" && imp=1
  printf '%s\n' "${backgrounds[@]}" | grep -qxF -- "$f" && bg=1
  if [ ! -f "$root/$f" ]; then echo "$f  missing"; bad=1; continue; fi
  if [ "$mode" = --apply ]; then
    tmp=$(mktemp)
    rewrite "$root/$f" "$imp" "$bg" > "$tmp"
    if cmp -s "$tmp" "$root/$f"; then
      rm -f "$tmp"
    else
      cp "$tmp" "$root/$f"; rm -f "$tmp"
      echo "rewrote $f"; changed=$((changed + 1))
    fi
  else
    out=$(problems "$f" "$imp" "$bg")
    if [ -n "$out" ]; then echo "$out"; bad=1; fi
  fi
done <<< "$all"

if [ "$mode" = --apply ]; then
  echo "skin imports: applied, $changed file(s) rewritten"
  exit "$bad"
fi
if [ "$bad" = 0 ]; then
  echo "skin imports: OK (${#imports[@]} files with skin imports, ${#backgrounds[@]} screens with the skin background)"
fi
exit "$bad"
