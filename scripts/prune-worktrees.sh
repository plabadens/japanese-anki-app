#!/usr/bin/env bash
# Prune Kanban worktrees and their branches once they are provably redundant.
#
#   ./scripts/prune-worktrees.sh          # dry run: print the plan, change nothing
#   ./scripts/prune-worktrees.sh --apply  # remove the planned worktrees/branches
#
# A branch is removable in exactly two cases:
#   merged      its tip is an ancestor of main (`git merge-base --is-ancestor`)
#   superseded  every commit on `main..<branch>` is already applied on main:
#               `git cherry main <branch>` prints no `+` lines (any `+` means at
#               least one commit's patch is absent, so the branch is kept)
# Everything else is reported as KEEP (real work) and never touched.
#
# A worktree is only removed when `git status --porcelain` is empty. Tracked
# modifications and untracked-but-not-ignored files abort that worktree; ignored
# build output does not count and is discarded by `git worktree remove --force`.
#
# Protected: main, dev, backup/*, every branch sitting on main's tip (local merge-train
# aliases), every branch matching a --keep glob, and all remote-tracking refs.
#
# A merge-train alias is only recognisable while its tip equals main's. As soon as a
# commit lands on main the alias looks like any other merged branch, so pass
#   --keep 'japanese-anki-app/t_3264cf90*' --keep 'japanese-anki-app/t_132e540a*'
# for refs that exist as labels rather than as work.
#
# Exit status: 0 on success (whether or not there was work), 2 on misuse.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

apply=0
keep_globs=()
while [ $# -gt 0 ]; do
  case "$1" in
    --apply) apply=1 ;;
    --keep) shift; [ $# -gt 0 ] || { echo "--keep needs a branch or glob" >&2; exit 2; }
            keep_globs+=("$1") ;;
    --keep=*) keep_globs+=("${1#--keep=}") ;;
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "usage: $0 [--apply] [--keep <branch-or-glob>]..." >&2; exit 2 ;;
  esac
  shift
done

main_ref=main
main_tip="$(git rev-parse --verify "${main_ref}^{commit}")"
root="$(git rev-parse --show-toplevel)"

is_kept_by_glob() {
  local branch="$1" glob
  for glob in "${keep_globs[@]:-}"; do
    [ -n "$glob" ] || continue
    # shellcheck disable=SC2053  # glob is intentional
    [[ "$branch" == $glob ]] && return 0
  done
  return 1
}

is_protected() {
  local branch="$1"
  case "$branch" in
    main | dev | backup/*) return 0 ;;
  esac
  is_kept_by_glob "$branch" && return 0
  # A merge-train alias is only identifiable while it still equals main's tip. Once
  # main advances it looks like any other merged branch, so pass --keep for refs that
  # exist as labels rather than as work.
  [ "$(git rev-parse "$branch")" = "$main_tip" ]
}

# branch -> worktree path ("" when the branch is not checked out anywhere)
declare -A wt_for=()
while IFS=$'\t' read -r path branch; do
  [ "$branch" = "DETACHED" ] && continue
  wt_for["$branch"]="$path"
done < <(git worktree list --porcelain | awk '
  /^worktree / { path = substr($0, 10) }
  /^branch /   { b = substr($0, 8); sub(/^refs\/heads\//, "", b); print path "\t" b; path = "" }
  /^detached/  { print path "\tDETACHED"; path = "" }')

plan_paths=()
plan_branches=()
plan_reasons=()
keep_lines=()
skip_lines=()

queue() { plan_paths+=("$1"); plan_branches+=("$2"); plan_reasons+=("$3"); }

while read -r branch; do
  [ -z "$branch" ] && continue
  if is_protected "$branch"; then
    skip_lines+=("protected   $branch")
    continue
  fi

  reason=""
  if git merge-base --is-ancestor "$branch" "$main_ref"; then
    reason="merged"
  else
    # `git cherry` marks already-applied patches with '-' and missing ones with '+'.
    # Empty output means the branch has no commits beyond main, i.e. the ancestor
    # test above should have caught it; treat it defensively as unproven.
    cherry="$(git cherry "$main_ref" "$branch")"
    if [ -n "$cherry" ] && ! printf '%s\n' "$cherry" | grep -q '^+'; then
      reason="superseded"
    fi
  fi

  if [ -z "$reason" ]; then
    keep_lines+=("$(printf '%-58s %3s commit(s) not on main' "$branch" "$(git rev-list --count "${main_ref}..${branch}")")")
    continue
  fi

  path="${wt_for[$branch]:-}"
  if [ -n "$path" ]; then
    if [ "$path" = "$root" ]; then
      skip_lines+=("checked out $branch")
      continue
    fi
    if [ -n "$(git -C "$path" status --porcelain)" ]; then
      skip_lines+=("dirty worktree $path ($branch)")
      continue
    fi
  fi
  queue "$path" "$branch" "$reason"
done < <(git for-each-ref --format='%(refname:short)' refs/heads)

# Detached worktrees carry no branch, so they are only removed when their HEAD is
# already reachable from main (nothing is lost) and the tree is clean.
while IFS=$'\t' read -r path branch; do
  [ "$branch" = "DETACHED" ] || continue
  [ "$path" = "$root" ] && continue
  head="$(git -C "$path" rev-parse HEAD)"
  if ! git merge-base --is-ancestor "$head" "$main_ref"; then
    keep_lines+=("$(printf '%-58s detached HEAD not on main' "$path")")
    continue
  fi
  if [ -n "$(git -C "$path" status --porcelain)" ]; then
    skip_lines+=("dirty worktree $path (detached)")
    continue
  fi
  queue "$path" "" "ancestor-of-main (detached)"
done < <(git worktree list --porcelain | awk '
  /^worktree / { path = substr($0, 10) }
  /^branch /   { path = "" }
  /^detached/  { print path "\tDETACHED"; path = "" }')

echo "repository: $root"
echo "main:       $(git rev-parse --short "$main_ref")"
echo "worktrees:  $(git worktree list --porcelain | grep -c '^worktree ')"
echo "branches:   $(git for-each-ref refs/heads | wc -l | tr -d ' ')"
echo

if [ "${#plan_paths[@]}" -eq 0 ]; then
  echo "Nothing to remove."
else
  echo "Would remove ${#plan_branches[@]} item(s):"
  for i in "${!plan_branches[@]}"; do
    printf '  %-9s %s' "${plan_reasons[$i]}" "${plan_paths[$i]:-<no worktree>}"
    [ -n "${plan_branches[$i]}" ] && printf '  [%s]' "${plan_branches[$i]}"
    printf '\n'
  done
fi

if [ "${#skip_lines[@]}" -gt 0 ]; then
  echo
  echo "Skipped:"
  printf '  %s\n' "${skip_lines[@]}"
fi

if [ "${#keep_lines[@]}" -gt 0 ]; then
  echo
  echo "Kept (carries work absent from main, or an unreachable detached HEAD):"
  printf '  %s\n' "${keep_lines[@]}"
fi

[ "$apply" -eq 1 ] || { echo; echo "Dry run. Re-run with --apply to perform these removals."; exit 0; }

echo
removed=0
for i in "${!plan_branches[@]}"; do
  path="${plan_paths[$i]}"
  branch="${plan_branches[$i]}"
  if [ -n "$path" ]; then
    git worktree remove --force "$path"
  fi
  if [ -n "$branch" ]; then
    git branch -D "$branch"
  fi
  removed=$((removed + 1))
done
git worktree prune
echo "Removed $removed item(s)."
echo "worktrees:  $(git worktree list --porcelain | grep -c '^worktree ')"
echo "branches:   $(git for-each-ref refs/heads | wc -l | tr -d ' ')"
