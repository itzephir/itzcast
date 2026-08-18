#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <base-commit> <head-commit>" >&2
  exit 2
fi

base_commit="$1"
head_commit="$2"

git cat-file -e "${base_commit}^{commit}"
git cat-file -e "${head_commit}^{commit}"

failed=0
checked=0

while IFS= read -r commit; do
  [[ -n "$commit" ]] || continue
  checked=$((checked + 1))

  author_name="$(git show -s --format=%an "$commit")"
  author_email="$(git show -s --format=%ae "$commit")"
  expected="Signed-off-by: ${author_name} <${author_email}>"
  trailers="$(git show -s --format=%B "$commit" | git interpret-trailers --parse)"

  if ! grep -Fxiq -- "$expected" <<<"$trailers"; then
    echo "::error::Commit ${commit} is missing the required trailer: ${expected}"
    failed=1
  fi
done < <(git rev-list --reverse "${base_commit}..${head_commit}")

if [[ "$checked" -eq 0 ]]; then
  echo "No commits to check."
elif [[ "$failed" -eq 0 ]]; then
  echo "DCO sign-off verified for ${checked} commit(s)."
fi

exit "$failed"
