#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-all}"
VULNERABLE_TAG="${CWE610_VULNERABLE_TAG:-cwe-610-vulnerable}"
MITIGATED_TAG="${CWE610_MITIGATED_TAG:-cwe-610-mitigated}"

if [[ "$MODE" != "all" && "$MODE" != "vulnerable" && "$MODE" != "mitigated" ]]; then
  echo "Usage: $0 [all|vulnerable|mitigated]" >&2
  exit 2
fi

ROOT="$(git rev-parse --show-toplevel)"
RESULT_ROOT="$ROOT/attacks/cwe-610/evidence"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/cwe610-experiment.XXXXXX")"

cleanup() {
  git -C "$ROOT" worktree remove --force "$TMP_ROOT/vulnerable" >/dev/null 2>&1 || true
  git -C "$ROOT" worktree remove --force "$TMP_ROOT/mitigated" >/dev/null 2>&1 || true
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

git -C "$ROOT" fetch --tags --quiet origin || true

require_tag() {
  local tag="$1"
  if ! git -C "$ROOT" rev-parse --verify --quiet "refs/tags/$tag^{commit}" >/dev/null; then
    echo "Required tag '$tag' is missing." >&2
    echo "Create the experiment snapshot tags first with:" >&2
    echo "  bash attacks/cwe-610/tag-snapshots.sh --push" >&2
    exit 2
  fi
}

copy_evidence() {
  local worktree="$1"
  local phase="$2"
  local generated="$worktree/attacks/cwe-610/evidence/$phase"
  local destination="$RESULT_ROOT/$phase"

  mkdir -p "$destination"
  for file in \
      read-request.txt \
      read-response.bin \
      overwrite-probe-request.txt \
      overwrite-probe-response.json \
      overwrite-request.txt \
      overwrite-response.json \
      target-before.txt \
      target-after.txt \
      result.txt; do
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

  echo "==> CWE-610 $phase: checking out tag $tag"
  git -C "$ROOT" worktree add --quiet --detach "$worktree" "$tag"
  mkdir -p "$evidence_dir"

  set +e
  (
    cd "$worktree"
    bash ./mvnw -B \
      -Dtest=com.zuehlke.securesoftwaredevelopment.controller.Cwe610ExperimentTests \
      -Dcwe610.phase="$phase" \
      test
  ) 2>&1 | tee "$evidence_dir/maven-output.txt"
  status=${PIPESTATUS[0]}
  set -e

  copy_evidence "$worktree" "$phase"
  git -C "$ROOT" worktree remove --force "$worktree"

  if [[ $status -ne 0 ]]; then
    echo "CWE-610 $phase experiment failed for tag $tag." >&2
    echo "See $evidence_dir/maven-output.txt" >&2
    exit $status
  fi

  echo "CWE-610 $phase experiment passed for tag $tag."
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
  echo "CWE-610 comparison passed: the same test succeeds with vulnerable expectations on $VULNERABLE_TAG and mitigated expectations on $MITIGATED_TAG."
fi
