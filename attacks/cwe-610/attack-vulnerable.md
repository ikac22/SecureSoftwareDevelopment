# CWE-610 — vulnerable application

## Goal

Demonstrate the externally controlled resource-reference weakness from issue #24 against the unmitigated personal-gallery implementation.

The experiment intentionally stays within the basic scenarios from the thesis:

1. read a harmless deterministic resource outside `galleryRoot` through `GET /gallery/image?path=...`;
2. overwrite a harmless, pre-existing fixture outside the authenticated customer's gallery through the upload flow.

No application policy, configuration, executable resource, or pricing file is modified.

## Entry points

Read:

```text
GET /gallery/image?path=../outside-read.txt
```

Controlled overwrite:

```text
POST /my-gallery/upload
fileName=../../outside-write.jpg
overwrite=false|true
```

The upload request is authenticated as customer `id = 1`.

## Source-to-sink flow

Read path:

```text
HTTP path
  -> PersonalGalleryController.image(...)
  -> PersonalGalleryService.loadForDisplay(...)
  -> Paths.get(requestedPath)
  -> galleryRoot.resolve(supplied).normalize()
  -> UrlResource
  -> HTTP response body
```

The application rejects absolute paths but does not verify that the normalized result still starts with `galleryRoot`.

Upload path:

```text
HTTP fileName / overwrite / image
  -> PersonalGalleryController.upload(...)
  -> PersonalGalleryService.store(...)
  -> userGallery.resolve(requestedFileName)
  -> Files.exists(destination)
  -> overwrite branch
  -> Files.copy(..., REPLACE_EXISTING)
  -> validateNewFileName(...) only for new targets
```

The validation-order flaw means an existing traversal target reaches the overwrite decision before the file name is validated.

## Deterministic harmless fixtures

The shared experiment test creates all resources inside a JUnit temporary directory:

```text
<temp>/
├── outside-read.txt          # known read marker
├── outside-write.jpg         # known writable target
└── user-galleries/
    └── 1/
```

The read fixture contains `CWE610_OUTSIDE_READ_FIXTURE`. The write target initially contains `CWE610_TARGET_BEFORE` and the attack attempts to replace it with `CWE610_TARGET_AFTER`.

This proves the primitive without touching real application resources.

## Versioned experiment snapshots

The exact vulnerable and mitigated states are represented by immutable tags:

- `cwe-610-vulnerable` — the last commit where the common attack harness is present and production code is still vulnerable;
- `cwe-610-mitigated` — the final state after the thesis mitigation is applied.

The same `Cwe610ExperimentTests` class and the same traversal values are executed against both snapshots. `attacks/cwe-610/tag-snapshots.sh` refuses to move an existing snapshot tag to another commit.

## Automated reproduction

From the repository root, after the two snapshot tags have been created:

```bash
bash attacks/cwe-610/run-attack.sh
```

A single phase can also be executed explicitly:

```bash
bash attacks/cwe-610/run-attack.sh vulnerable
bash attacks/cwe-610/run-attack.sh mitigated
```

The runner resolves the two tags, creates temporary detached worktrees, and invokes exactly the same test class with only the expected phase changed through `-Dcwe610.phase=...`.

## Vulnerable PASS criteria

The vulnerable snapshot passes only when all of the following are observed:

- the read request returns HTTP `200` and contains the known marker from `outside-read.txt`;
- the first upload with `overwrite=false` returns `REQUIRES_OVERWRITE` for the traversal target, showing that the application reached the existing-target branch before sanitization;
- the same `fileName` with `overwrite=true` replaces the harmless outside fixture;
- the legitimate upload/overwrite/display positive control still works.

The write primitive is deliberately described narrowly: this path can overwrite a pre-existing writable target; it does not demonstrate arbitrary creation of a new file outside the gallery.

## Evidence

The harness writes phase-specific evidence under:

```text
attacks/cwe-610/evidence/<phase>/
├── read-request.txt
├── read-response.bin
├── overwrite-probe-request.txt
├── overwrite-probe-response.json
├── overwrite-request.txt
├── overwrite-response.json
├── target-before.txt
├── target-after.txt
├── result.txt
└── maven-output.txt
```

The committed vulnerable evidence records the expected observable effects for the last unmitigated snapshot. The snapshot runner regenerates the evidence from the tagged source trees when the experiment is executed.
