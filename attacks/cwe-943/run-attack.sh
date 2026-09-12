#!/usr/bin/env bash
set -euo pipefail

PHASE="${1:-vulnerable}"
if [[ "$PHASE" != "vulnerable" && "$PHASE" != "mitigated" ]]; then
  echo "Usage: $0 [vulnerable|mitigated]" >&2
  exit 2
fi

EVIDENCE_DIR="attacks/cwe-943/evidence/$PHASE"
mkdir -p "$EVIDENCE_DIR"

set +e
./mvnw -B \
  -Dtest=com.zuehlke.securesoftwaredevelopment.controller.Cwe943ExperimentTests \
  -Dcwe943.phase="$PHASE" \
  test 2>&1 | tee "$EVIDENCE_DIR/maven-output.txt"
STATUS=${PIPESTATUS[0]}
set -e

if [[ $STATUS -ne 0 ]]; then
  echo "CWE-943 $PHASE experiment failed. See $EVIDENCE_DIR/maven-output.txt" >&2
  exit $STATUS
fi

echo "CWE-943 $PHASE experiment passed. Evidence written to $EVIDENCE_DIR"
