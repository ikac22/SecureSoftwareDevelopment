#!/usr/bin/env bash
set -euo pipefail

PUSH=false
if [[ "${1:-}" == "--push" ]]; then
  PUSH=true
elif [[ $# -gt 0 ]]; then
  echo "Usage: $0 [--push]" >&2
  exit 2
fi

ROOT="$(git rev-parse --show-toplevel)"
VULNERABLE_TAG="${CWE94_VULNERABLE_TAG:-cwe-94-vulnerable}"
MITIGATED_TAG="${CWE94_MITIGATED_TAG:-cwe-94-mitigated}"
VULNERABLE_COMMIT="${CWE94_VULNERABLE_COMMIT:-d92e8b3723b479833f35fc5af7f84777698220ab}"
MITIGATED_REF="${CWE94_MITIGATED_REF:-attack/cwe-94-loyalty-spel}"

vulnerable_commit="$(git -C "$ROOT" rev-parse "$VULNERABLE_COMMIT^{commit}")"
mitigated_commit="$(git -C "$ROOT" rev-parse "$MITIGATED_REF^{commit}")"

if [[ "$vulnerable_commit" == "$mitigated_commit" ]]; then
  echo "Vulnerable and mitigated snapshots resolve to the same commit." >&2
  exit 2
fi

if ! git -C "$ROOT" merge-base --is-ancestor "$vulnerable_commit" "$mitigated_commit"; then
  echo "The vulnerable snapshot is not an ancestor of the mitigated snapshot." >&2
  exit 2
fi

create_tag() {
  local tag="$1"
  local target="$2"
  local message="$3"
  local existing

  existing="$(git -C "$ROOT" rev-parse --verify --quiet "refs/tags/$tag^{commit}" || true)"
  if [[ -n "$existing" ]]; then
    if [[ "$existing" != "$target" ]]; then
      echo "Tag '$tag' already exists and points to $existing instead of $target." >&2
      echo "Snapshot tags are immutable experiment references; refusing to move it." >&2
      exit 2
    fi
    echo "Tag '$tag' already points to $target."
    return
  fi

  git -C "$ROOT" tag -a "$tag" "$target" -m "$message"
  echo "Created tag '$tag' -> $target"
}

create_tag "$VULNERABLE_TAG" "$vulnerable_commit" \
  "CWE-94 vulnerable experiment snapshot"
create_tag "$MITIGATED_TAG" "$mitigated_commit" \
  "CWE-94 mitigated experiment snapshot"

if $PUSH; then
  git -C "$ROOT" push origin "refs/tags/$VULNERABLE_TAG" "refs/tags/$MITIGATED_TAG"
  echo "Pushed both experiment snapshot tags to origin."
else
  echo "Tags were created locally only. Push them with:"
  echo "  git push origin refs/tags/$VULNERABLE_TAG refs/tags/$MITIGATED_TAG"
fi
