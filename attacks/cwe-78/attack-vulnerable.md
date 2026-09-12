# CWE-78 — vulnerable application

## Goal

Demonstrate the command/argument injection described in issue #21 against the unmitigated service-document bundle implementation.

The experiment keeps the normal ownership and `COMPLETED`-service checks intact. The only manipulated input is the repeated HTTP `files` parameter.

## Entry point

`POST /services/{serviceId}/documents/bundle`

The automated experiment authenticates a customer that owns completed service `127` and sends the same two `files` values in both application states:

```text
files=service-overview.pdf
files=--to-command=cat>service-overview.pdf;touch${IFS}bundle-extra.pdf
```

The first value is a legitimate service document. The second value is shaped like a PDF name closely enough to pass the vulnerable validation, but GNU `tar` can interpret it as the `--to-command` option.

## Source-to-sink flow

```text
HTTP files parameters
        |
        v
ServiceDocumentBundleController.downloadBundle(...)
        |
        v
ServiceDocumentBundleService.normalizeSelection(...)
        |
        v
List<String> extractionArguments
        |
        v
command.addAll(extractionArguments)
        |
        v
ProcessBuilder(command)
        |
        v
GNU tar argument parser
```

The vulnerable implementation validates only the general shape of each value and requires that at least one selected element belongs to `ALLOWED_DOCUMENTS`. It does not require every element to map to a server-owned document identifier, and it does not insert `--` before the request-controlled operands.

As a result, a request value can retain option syntax when it reaches `tar`.

## Observable effect

For the vulnerable snapshot, GNU `tar` is expected to treat the crafted second `files` value as `--to-command`. The harmless command recreates the selected overview document from standard input and creates `bundle-extra.pdf` in the temporary extraction directory.

The application then builds the response TAR from that directory. Therefore the attack has a deterministic external marker: the returned archive contains `bundle-extra.pdf`, even though that file was not part of the persistent service-document archive.

No reverse shell, destructive command, path traversal, or ownership bypass is required for this experiment.

## Shared experiment harness

`Cwe78ExperimentTests` is used unchanged for both snapshots. Its phase is selected with:

```text
-Dcwe78.phase=vulnerable
-Dcwe78.phase=mitigated
```

For the vulnerable phase it requires:

- HTTP `200` from the real bundle controller path;
- a readable returned TAR archive;
- `bundle-extra.pdf` present in that archive.

The test writes the request, response headers, raw response TAR, TAR member listing, and boolean result to `attacks/cwe-78/evidence/vulnerable/`.

## Versioned snapshots

The final experiment uses two immutable tags:

- `cwe-78-vulnerable` — the last commit where the shared harness exists and the production implementation is still vulnerable;
- `cwe-78-mitigated` — the final commit after the mitigation is implemented and the same attack is rejected.

`attacks/cwe-78/tag-snapshots.sh` creates the tags without moving an existing tag. `attacks/cwe-78/run-attack.sh` checks the tags out into temporary detached worktrees and runs the same test against each snapshot.

## Evidence

The harness generates:

```text
attacks/cwe-78/evidence/vulnerable/
├── request.txt
├── response-headers.txt
├── response.tar
├── tar-contents.txt
├── result.txt
└── maven-output.txt
```

The important PASS condition is not merely the HTTP status. It is the presence of `bundle-extra.pdf` in the TAR returned by the vulnerable application after the crafted `files` value reaches the actual GNU `tar` process.
