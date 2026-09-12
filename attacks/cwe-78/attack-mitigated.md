# CWE-78 — mitigated application

## Mitigation

The mitigation follows the model described in issue #21 and in the thesis:

1. every requested document must match one exact server-owned value from `ALLOWED_DOCUMENTS`;
2. unsupported values are rejected as `400 Bad Request` before the external process is started;
3. the GNU `tar` invocation additionally inserts `--` before the selected document names so subsequent argv elements are treated as operands rather than options.

The normal ownership and completed-service checks remain unchanged.

## Relevant change

The vulnerable implementation accepted any PDF-like value as long as at least one selected element was allowed. The mitigated implementation instead requires every normalized value to belong to the closed allow-list:

```text
service-overview.pdf
parts-detailed.pdf
work-detailed.pdf
```

The extraction command is structurally separated from the operands:

```text
tar -xzf <persistent-archive> -C <temporary-directory> -- <canonical-document-names>
```

The allow-list is the primary trust-boundary fix. The `--` separator is an additional structural defense at the command-line parser boundary.

## Repeated attack

The shared `Cwe78ExperimentTests` harness sends exactly the same attack values as in the vulnerable experiment:

```text
files=service-overview.pdf
files=--to-command=cat>service-overview.pdf;touch${IFS}bundle-extra.pdf
```

For the mitigated snapshot, the expected externally visible result is now an invalid request:

- HTTP status is `400`;
- the crafted second value is rejected before `tar` starts;
- no `bundle-extra.pdf` marker is created;
- no successful attack TAR is returned.

This is intentionally stronger and clearer than returning a normal `200` response whose archive merely lacks the marker: the request is outside the supported document-selection contract and is rejected at the validation boundary.

## Positive control

After the failed attack, the same experiment performs a normal request with:

```text
files=service-overview.pdf
files=work-detailed.pdf
```

That request must still return HTTP `200` and a valid TAR containing exactly the two selected allowed documents. This verifies that the mitigation removes the injection path without breaking the intended bundle functionality.

## Snapshot reproduction

Create immutable local tags, optionally pushing them to the remote repository:

```bash
bash attacks/cwe-78/tag-snapshots.sh --push
```

Then execute the same harness against both tagged source trees:

```bash
bash attacks/cwe-78/run-attack.sh
```

The runner checks each snapshot out into a temporary detached worktree and executes:

```text
Cwe78ExperimentTests + -Dcwe78.phase=vulnerable
Cwe78ExperimentTests + -Dcwe78.phase=mitigated
```

Evidence is kept separately under:

```text
attacks/cwe-78/evidence/vulnerable/
attacks/cwe-78/evidence/mitigated/
```

Each run records the exact request, response headers, response bytes, TAR member listing where applicable, result flags, and Maven output.

## PASS criteria

The mitigated experiment passes only when:

- the same crafted request returns `400 Bad Request`;
- `bundle-extra.pdf` is not created or returned;
- a normal request containing only supported document names still returns a valid TAR with the selected files.

Together with the vulnerable snapshot, this demonstrates the before/after effect of the thesis mitigation using one unchanged attack harness and one unchanged payload.
