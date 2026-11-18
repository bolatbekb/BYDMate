#!/usr/bin/env bash
# Fork marks check: every change the fork made inside an AUTHOR file (a file that exists in the
# upstream base) must carry the word "fork" (any case) in the same diff hunk — a `// fork: …` line,
# a `<!-- fork … -->` comment or a `# fork` comment. A pure deletion gets a `// fork: removed …`
# comment in its place, so it shows up as a hunk with a marked added line.
#
# usage: tools/fork-marks.sh [base]   (default: git merge-base HEAD upstream/main)
# Compares the base with the WORKING TREE, so uncommitted edits are checked too.
# exit 0 = every hunk marked; exit 1 = unmarked hunks listed (file:line and the first added line).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
base="${1:-$(git merge-base HEAD upstream/main)}"
bad=0
while IFS= read -r f; do
  # -U0: one hunk per contiguous change; awk checks each hunk for the word "fork".
  out=$(git diff -U0 "$base" -- "$f" | awk -v file="$f" '
    function flush() {
      if (inhunk && !marked) { printf "%s:%s  %s\n", file, start, (first == "" ? "(pure deletion)" : first) }
    }
    /^@@/ {
      flush()
      inhunk = 1; marked = 0; first = ""
      split($3, a, ","); start = substr(a[1], 2)
      next
    }
    /^\+\+\+|^---/ { next }
    /^\+/ {
      line = substr($0, 2)
      if (tolower(line) ~ /fork/) marked = 1
      if (first == "" && line !~ /^[[:space:]]*$/) first = substr(line, 1, 100)
      next
    }
    END { flush() }
  ')
  if [ -n "$out" ]; then echo "$out"; bad=1; fi
done < <(git diff --name-only --diff-filter=M "$base")
if [ "$bad" = 0 ]; then echo "fork marks: every change in an author file is marked (base ${base:0:8})"; fi
exit "$bad"
