#!/usr/bin/env bash
# skin imports: restores and checks the fork's skin hooks in AUTHOR files, from one list.
#
# The skin reaches author screens through one-line hooks, each marked `// fork: skin…`:
#   [imports]     `import androidx.compose.material3.{Switch,Card,Slider,HorizontalDivider,AlertDialog,
#                 NavigationBar,NavigationBarItem,Button,OutlinedButton,TextButton,FilterChip,Checkbox,
#                 RadioButton,OutlinedTextField,Text}`
#                 and `import androidx.compose.foundation.shape.RoundedCornerShape` become
#                 `import com.bydmate.app.ui.skin.<Name> // fork: skin`. The call sites stay the
#                 author's: the fork functions have the same names, parameters and defaults.
#                 Only the names a file actually imports are touched.
#                 STAR imports: a file with `import androidx.compose.material3.*` (or
#                 `foundation.shape.*`) that calls a swapped name (word-boundary search for `Name(`,
#                 or `Name {` for a trailing-lambda call, outside comments) gets an explicit
#                 `import com.bydmate.app.ui.skin.<Name> // fork: skin` after its last import line;
#                 an explicit import wins over a star import in Kotlin. Only the names the file
#                 really uses are added.
#                 ALIAS imports (`import androidx.compose.material3.Card as M3Card`) are never touched
#                 by --apply; --check reports them as "alias, review by hand" and fails.
#                 FULLY QUALIFIED calls (`androidx.compose.material3.FilterChip(`) bypass any import:
#                 --apply leaves them, --check reports them as "fully qualified, review by hand" and
#                 fails. The fix is a marked one-token rewrite of that line:
#                 `com.bydmate.app.ui.skin.<Name>( // fork: skin`.
#                 FROZEN shapes: a top-level `val X = RoundedCornerShape(…)` runs once, so it keeps the
#                 skin of its first use until a restart. --check reports it as "frozen shape" and
#                 fails. The fix is a marked getter on that line:
#                 `val X get() = RoundedCornerShape(…) // fork: skin, live`.
#                 The swapped names live in ONE place: SKIN_M3_NAMES / SKIN_SHAPE_NAMES below
#                 (adding a name is a one-line change; SkinImportsTest reads those two lines).
#   [background]  `.background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))` becomes
#                 `.skinBackground() // fork: skin background`, and the file gets
#                 `import com.bydmate.app.ui.skin.skinBackground // fork: skin`: it takes the place
#                 of `import androidx.compose.ui.graphics.Brush` when nothing else in the file uses
#                 Brush (so the hunk stays marked), otherwise it goes after the last import.
#   [skip]        author files that use swapped names but stay M3 on purpose: Compose windows outside
#                 BYDMateTheme (overlays, widgets). The tool ignores them; --check only says when a
#                 skipped file is missing or also sits in [imports] / [background].
#
# tools/skin-imports.list names the files (paths from the repo root) under `[imports]`,
# `[background]` and `[skip]`; a file may sit in both of the first two; `#` starts a note.
# SkinImportsTest reads the same list.
#
# tools/skin-author-files.txt is GENERATED: every author Kotlin file under app/src/main/kotlin, that
# is every one in the upstream base (git merge-base HEAD upstream/main). SkinImportsTest reads it to
# fail on an author file that uses a swapped name and is in neither [imports] nor [skip] (a new or
# changed upstream screen). The unit tests run without git (CI has no upstream remote), so the list
# is committed. --apply refreshes it and --check fails when it is stale; both skip that step where
# upstream/main is unknown.
#
# usage: tools/skin-imports.sh --apply   rewrite the listed files and refresh the author list;
#                                        idempotent (a second run changes nothing)
#        tools/skin-imports.sh --check   exit 1 and list every listed file that still has a raw import,
#                                        a fully qualified call, a frozen shape or the raw background
#                                        (or lost a hook), and a stale author list; exit 0 prints OK
# After an upstream merge: tools/skin-imports.sh --apply && tools/skin-imports.sh --check, then
# tools/fork-marks.sh and a build. When SkinImportsTest names an unlisted author file: add it to
# [imports] (or [skip], for a window outside the app theme) and run --apply.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
list="$root/tools/skin-imports.list"
authors="$root/tools/skin-author-files.txt"
mode="${1:-}"
case "$mode" in
  --apply|--check) ;;
  *) echo "usage: tools/skin-imports.sh --apply|--check" >&2; exit 2 ;;
esac
[ -f "$list" ] || { echo "skin imports: no list at $list" >&2; exit 2; }

# The ONE list of swapped names. SkinImportsTest parses these two lines; keep the format.
SKIN_M3_NAMES="Switch Card Slider HorizontalDivider AlertDialog NavigationBar NavigationBarItem Button OutlinedButton TextButton FilterChip Checkbox RadioButton OutlinedTextField Text"   # androidx.compose.material3.<Name>
SKIN_SHAPE_NAMES="RoundedCornerShape"                              # androidx.compose.foundation.shape.<Name>

# entries <section>: the paths listed under [section]
entries() {
  awk -v want="[$1]" '
    { sub(/#.*/, ""); gsub(/^[ \t]+|[ \t]+$/, "") }
    $0 == "" { next }
    /^\[.*\]$/ { sec = $0; next }
    sec == want { print }
  ' "$list"
}

# author_files: the generated tools/skin-author-files.txt on stdout. Fails where the upstream base is
# unknown: no git, a copy outside the repo, or no upstream remote (CI).
author_files() {
  local top base
  top=$(git -C "$root" rev-parse --show-toplevel 2>/dev/null) || return 1
  [ "$(cd "$top" && pwd -P)" = "$root" ] || return 1
  base=$(git -C "$root" merge-base HEAD upstream/main 2>/dev/null) || return 1
  echo "# GENERATED by tools/skin-imports.sh --apply, do not edit: every author Kotlin file under"
  echo "# app/src/main/kotlin, as in the upstream base (git merge-base HEAD upstream/main)."
  echo "# SkinImportsTest reads it: an author file that uses a swapped name must be in [imports] or [skip]."
  git -C "$root" ls-tree -r --name-only "$base" -- app/src/main/kotlin | { grep '\.kt$' || true; } | LC_ALL=C sort
}

# Shared awk prelude: name tables (pkg of each name), use detection and fully qualified calls.
AWK_COMMON='
function initNames(   i, c) {
  nn = 0
  c = split(m3, t, " "); for (i = 1; i <= c; i++) { nm[++nn] = t[i]; pk[nn] = "androidx\\.compose\\.material3"; shape[nn] = 0 }
  c = split(shp, t, " "); for (i = 1; i <= c; i++) { nm[++nn] = t[i]; pk[nn] = "androidx\\.compose\\.foundation\\.shape"; shape[nn] = 1 }
}
function comment(s) { return (s ~ /^[ \t]*(\/\/|\*|\/\*)/) }
# a call of name: `Name(`, or `Name {` (a trailing lambda), not after a dot or inside a longer name
function uses(s, name) {
  if (comment(s)) return 0
  return (s ~ ("(^|[^A-Za-z0-9_.])" name "[ \t]*[({]"))
}
# a fully qualified call of name k: `androidx.compose.material3.Name(` bypasses any import
function fqn(s, k) {
  if (comment(s)) return 0
  return (s ~ ("(^|[^A-Za-z0-9_.])" pk[k] "\\." nm[k] "[ \t]*[({]"))
}
function indent(s) { match(s, /^[ \t]*/); return RLENGTH }
'

# rewrite <file> <imports 0|1> <background 0|1>: prints the rewritten file on stdout
rewrite() {
  awk -v m3="$SKIN_M3_NAMES" -v shp="$SKIN_SHAPE_NAMES" -v doImports="$2" -v doBg="$3" "$AWK_COMMON"'
    { line[++n] = $0 }
    END {
      initNames()
      raw = ".background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))"
      hook = ".skinBackground()"
      skinImport = "import com.bydmate.app.ui.skin.skinBackground // fork: skin"
      hasHook = 0; hasImport = 0; brushAt = 0; brushUsed = 0; lastImport = 0; pkgAt = 0
      star3 = 0; starShp = 0
      for (i = 1; i <= n; i++) {
        s = line[i]
        if (s ~ /^package /) pkgAt = i
        if (s ~ /^import /) {
          lastImport = i
          if (doImports) {
            for (k = 1; k <= nn; k++)
              if (s ~ ("^import " pk[k] "\\." nm[k] "[ \t]*$"))
                s = "import com.bydmate.app.ui.skin." nm[k] " // fork: skin"
            if (s ~ /^import androidx\.compose\.material3\.\*[ \t]*$/) star3 = 1
            if (s ~ /^import androidx\.compose\.foundation\.shape\.\*[ \t]*$/) starShp = 1
            for (k = 1; k <= nn; k++)
              if (s == "import com.bydmate.app.ui.skin." nm[k] " // fork: skin") present[k] = 1
          }
          if (s == skinImport) hasImport = 1
          if (s ~ /^import androidx\.compose\.ui\.graphics\.Brush[ \t]*$/) brushAt = i
        } else {
          if (doImports)
            for (k = 1; k <= nn; k++) if (uses(s, nm[k])) used[k] = 1
          if (doBg) {
            changed = 0
            while ((p = index(s, raw)) > 0) {
              s = substr(s, 1, p - 1) hook substr(s, p + length(raw)); changed = 1
            }
            if (changed && tolower(s) !~ /fork/) { sub(/[ \t]+$/, "", s); s = s " // fork: skin background" }
            if (index(s, hook) > 0) hasHook = 1
            if (s ~ /(^|[^A-Za-z0-9_])Brush([^A-Za-z0-9_]|$)/) brushUsed = 1
          }
        }
        out[i] = s
      }
      extra = ""
      if (doImports)
        for (k = 1; k <= nn; k++) {
          isStar = (pk[k] ~ /material3/) ? star3 : starShp
          if (isStar && used[k] && !present[k]) extra = extra "import com.bydmate.app.ui.skin." nm[k] " // fork: skin\n"
        }
      if (doBg && hasHook && !hasImport) {
        if (brushAt && !brushUsed) out[brushAt] = skinImport
        else extra = extra skinImport "\n"
      }
      insertAfter = (lastImport ? lastImport : pkgAt)
      for (i = 1; i <= n; i++) {
        print out[i]
        if (i == insertAfter && extra != "") printf "%s", extra
      }
    }
  ' "$1"
}

# problems <file> <imports 0|1> <background 0|1>: one line per problem
problems() {
  awk -v f="$1" -v m3="$SKIN_M3_NAMES" -v shp="$SKIN_SHAPE_NAMES" -v doImports="$2" -v doBg="$3" "$AWK_COMMON"'
    BEGIN { initNames(); objInd = -1 }
    # A swapped shape held in a top-level (or object / companion) val is made once and keeps the
    # skin of its first use. Only for a shape the file imports (raw, skin or star); a getter is live.
    function frozen(s,   k, lead) {
      if (objInd < 0 && s ~ /^[ \t]*((private|internal|public|protected|data)[ \t]+)*(companion[ \t]+)?object([ \t]|\{|$)/ && s ~ /\{[ \t]*$/) {
        objInd = indent(s); pending = 0; return
      }
      if (objInd >= 0 && indent(s) == objInd && s ~ /^[ \t]*\}/) { objInd = -1; pending = 0; return }
      lead = (objInd >= 0) ? objInd + 4 : 0
      decl = (indent(s) == lead && s ~ /^[ \t]*((private|internal|public|protected|const|override)[ \t]+)*val[ \t]/ && s !~ /[ \t]get[ \t]*\([ \t]*\)/)
      if (comment(s)) return
      for (k = 1; k <= nn; k++) {
        if (!shape[k] || !(imported[k] || starShp)) continue
        if ((decl || pending) && s ~ ("(^|[^A-Za-z0-9_.])" nm[k] "[ \t]*\\("))
          printf "%s:%d  frozen shape: a %s( in a top-level or object val is made once (make it a getter: val X get() = %s(...) // fork: skin, live)\n", f, NR, nm[k], nm[k]
      }
      # `val X =` or `val X by lazy {` with the shape on the next line
      if (s ~ /^[ \t]*$/) return
      pending = (decl && s ~ /(=|\{)[ \t]*$/)
    }
    {
      if (doImports) {
        if ($0 ~ /^import /) {
          for (k = 1; k <= nn; k++) {
            if ($0 ~ ("^import " pk[k] "\\." nm[k] "[ \t]*$")) printf "%s:%d  raw import of %s\n", f, NR, nm[k]
            if ($0 ~ ("^import " pk[k] "\\." nm[k] "[ \t]+as[ \t]")) printf "%s:%d  alias, review by hand: %s\n", f, NR, $0
            if ($0 == "import com.bydmate.app.ui.skin." nm[k] " // fork: skin") { skinImports++; present[k] = 1; imported[k] = 1 }
            if ($0 ~ ("^import " pk[k] "\\." nm[k] "[ \t]*$")) imported[k] = 1
          }
          if ($0 ~ /^import androidx\.compose\.material3\.\*[ \t]*$/) star3 = 1
          if ($0 ~ /^import androidx\.compose\.foundation\.shape\.\*[ \t]*$/) starShp = 1
        } else {
          for (k = 1; k <= nn; k++) {
            if (uses($0, nm[k]) && !used[k]) { used[k] = 1; usedAt[k] = NR }
            if (fqn($0, k)) printf "%s:%d  fully qualified, review by hand: %s( bypasses the skin import (write com.bydmate.app.ui.skin.%s( // fork: skin)\n", f, NR, nm[k], nm[k]
          }
          frozen($0)
        }
      }
      if (doBg) {
        if (index($0, "background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))") > 0)
          printf "%s:%d  raw background gradient\n", f, NR
        if (index($0, ".skinBackground() // fork: skin background") > 0) hooks++
        if ($0 == "import com.bydmate.app.ui.skin.skinBackground // fork: skin") bgImport++
      }
    }
    END {
      if (doImports) {
        for (k = 1; k <= nn; k++) {
          isStar = (pk[k] ~ /material3/) ? star3 : starShp
          if (isStar && used[k] && !present[k])
            printf "%s:%d  star import, %s( used without the explicit skin import\n", f, usedAt[k], nm[k]
        }
        if (!skinImports) printf "%s  no skin import at all (drop it from [imports]?)\n", f
      }
      if (doBg && !hooks) printf "%s  no skinBackground hook\n", f
      if (doBg && hooks && !bgImport) printf "%s  skinBackground without its import\n", f
    }
  ' "$root/$1"
}

mapfile -t imports < <(entries imports)
mapfile -t backgrounds < <(entries background)
mapfile -t skips < <(entries skip)
all=$(printf '%s\n' "${imports[@]}" "${backgrounds[@]}" | awk 'NF && !seen[$0]++')

bad=0; changed=0
# [skip]: never rewritten or checked; it only has to name real files that are not also hooked
if [ "$mode" = --check ]; then
  for f in "${skips[@]}"; do
    if [ ! -f "$root/$f" ]; then echo "$f  missing (in [skip])"; bad=1; fi
    if printf '%s\n' "$all" | grep -qxF -- "$f"; then echo "$f  in [skip] and also hooked: pick one"; bad=1; fi
  done
fi
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

# the author list (SkinImportsTest's guard of the unlisted author files), from the upstream base
authorNote=""
if gen=$(author_files); then
  have=$(cat "$authors" 2>/dev/null || true)
  if [ "$gen" != "$have" ]; then
    if [ "$mode" = --apply ]; then
      printf '%s\n' "$gen" > "$authors"; echo "refreshed tools/skin-author-files.txt"
    else
      echo "tools/skin-author-files.txt  stale or missing (the upstream base moved): run tools/skin-imports.sh --apply"; bad=1
    fi
  fi
else
  authorNote="; author list not verified, no upstream/main here"
  [ "$mode" = --apply ] && echo "skin imports: no upstream/main here, tools/skin-author-files.txt left as it is" >&2
fi

if [ "$mode" = --apply ]; then
  echo "skin imports: applied, $changed file(s) rewritten"
  exit "$bad"
fi
if [ "$bad" = 0 ]; then
  echo "skin imports: OK (${#imports[@]} files with skin imports, ${#backgrounds[@]} screens with the skin background, ${#skips[@]} skipped$authorNote)"
fi
exit "$bad"
