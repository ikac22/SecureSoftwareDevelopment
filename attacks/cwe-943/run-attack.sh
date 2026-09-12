#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-all}"
VULNERABLE_TAG="${CWE943_VULNERABLE_TAG:-cwe-943-vulnerable}"
MITIGATED_TAG="${CWE943_MITIGATED_TAG:-cwe-943-mitigated}"

if [[ "$MODE" != "all" && "$MODE" != "vulnerable" && "$MODE" != "mitigated" ]]; then
  echo "Usage: $0 [all|vulnerable|mitigated]" >&2
  exit 2
fi

ROOT="$(git rev-parse --show-toplevel)"
RESULT_ROOT="$ROOT/attacks/cwe-943/evidence"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/cwe943-experiment.XXXXXX")"

cleanup() {
  git -C "$ROOT" worktree remove --force "$TMP_ROOT/vulnerable" >/dev/null 2>&1 || true
  git -C "$ROOT" worktree remove --force "$TMP_ROOT/mitigated" >/dev/null 2>&1 || true
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

# Prefer the repository tags even when the local checkout has not fetched them yet.
git -C "$ROOT" fetch --tags --quiet origin || true

require_tag() {
  local tag="$1"
  if ! git -C "$ROOT" rev-parse --verify --quiet "refs/tags/$tag^{commit}" >/dev/null; then
    echo "Required tag '$tag' is missing." >&2
    echo "Create the experiment snapshot tags first with:" >&2
    echo "  bash attacks/cwe-943/tag-snapshots.sh --push" >&2
    exit 2
  fi
}

copy_evidence() {
  local worktree="$1"
  local phase="$2"
  local generated="$worktree/attacks/cwe-943/evidence/$phase"
  local destination="$RESULT_ROOT/$phase"

  mkdir -p "$destination"
  for file in request.json response.json result.txt; do
    if [[ -f "$generated/$file" ]]; then
      cp "$generated/$file" "$destination/$file"
    fi
  done
}

run_phase() {
  local phase="$1"
  local tag="$2"
  local worktree="$TMP_ROOT/$phase"
  local evidence_dir="$RESULT_ROOT/$phase"

  echo "==> CWE-943 $phase: checking out tag $tag"
  git -C "$ROOT" worktree add --quiet --detach "$worktree" "$tag"
  mkdir -p "$evidence_dir"

  set +e
  (
    cd "$worktree"
    bash ./mvnw -B \
      -Dtest=com.zuehlke.securesoftwaredevelopment.controller.Cwe943ExperimentTests \
      -Dcwe943.phase="$phase" \
      test
  ) 2>&1 | tee "$evidence_dir/maven-output.txt"
  status=${PIPESTATUS[0]}
  set -e

  # The test writes request/response/result inside the checked-out snapshot.
  # Copy them back to the caller's worktree before removing the temporary one.
  copy_evidence "$worktree" "$phase"
  git -C "$ROOT" worktree remove --force "$worktree"

  if [[ $status -ne 0 ]]; then
    echo "CWE-943 $phase experiment failed for tag $tag." >&2
    echo "See $evidence_dir/maven-output.txt" >&2
    exit $status
  fi

  echo "CWE-943 $phase experiment passed for tag $tag."
}

if [[ "$MODE" == "all" || "$MODE" == "vulnerable" ]]; then
  require_tag "$VULNERABLE_TAG"
  run_phase vulnerable "$VULNERABLE_TAG"
fi

if [[ "$MODE" == "all" || "$MODE" == "mitigated" ]]; then
  require_tag "$MITIGATED_TAG"
  run_phase mitigated "$MITIGATED_TAG"
fi

if [[ "$MODE" == "all" ]]; then
  echo "CWE-943 comparison passed: the same test succeeds with vulnerable expectations on $VULNERABLE_TAG and mitigated expectations on $MITIGATED_TAG."
fi
