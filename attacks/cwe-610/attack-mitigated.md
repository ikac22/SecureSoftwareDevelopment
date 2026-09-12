# CWE-610 — mitigated application

## Mitigation

The personal-gallery flow now enforces the invariant described in issue #24 and the thesis: after a client-controlled reference is resolved and normalized, the resulting resource must remain inside its allowed root.

For display requests, `PersonalGalleryService.loadForDisplay(...)` resolves the requested relative path against the normalized absolute `galleryRoot` and rejects the request unless the resulting path starts with that root.

For uploads, the requested file name is validated before any `Files.exists(...)` check or overwrite decision. The final destination is also resolved against the authenticated customer's normalized gallery root and checked for containment before any file operation.

The mitigation deliberately keeps the existing upload semantics for legitimate `.jpg` / `.png` names, including the existing `REQUIRES_OVERWRITE` confirmation flow.

## Same attack, same harness

The mitigated experiment does not introduce a second attack implementation. `Cwe610ExperimentTests` is the same test class that exists in the `cwe-610-vulnerable` snapshot and sends the same values:

```text
GET /gallery/image?path=../outside-read.txt

POST /my-gallery/upload
fileName=../../outside-write.jpg
overwrite=false

POST /my-gallery/upload
fileName=../../outside-write.jpg
overwrite=true
```

The only change between runs is `-Dcwe610.phase=vulnerable|mitigated`, which selects the expected outcome.

## Mitigated PASS criteria

The mitigated snapshot passes only when:

- the read traversal no longer returns the `CWE610_OUTSIDE_READ_FIXTURE` marker;
- the upload traversal is rejected before the outside target can be used as an overwrite oracle;
- the `overwrite=true` request leaves the harmless outside target unchanged as `CWE610_TARGET_BEFORE`;
- a legitimate upload inside customer `1`'s gallery succeeds;
- a legitimate existing image still produces the normal overwrite-confirmation response;
- confirming that overwrite succeeds; and
- the legitimate image can still be retrieved through `/gallery/image`.

## Snapshot reproduction

Create immutable snapshot tags from the experiment branch:

```bash
bash attacks/cwe-610/tag-snapshots.sh --push
```

The vulnerable tag is pinned to commit `57e2d7d8997d0bd6791714747d6d64ffeb6c20cf`, the last branch state where the common harness and vulnerable evidence are present while `PersonalGalleryService` is still unmitigated. The mitigated tag resolves to the finalized experiment branch state.

Then run both states using temporary detached worktrees:

```bash
bash attacks/cwe-610/run-attack.sh
```

The runner fails if either tag is missing, if the vulnerable snapshot no longer demonstrates the weakness, or if the mitigated snapshot still demonstrates the vulnerable effect.

## Evidence

Each execution writes its own evidence under:

```text
attacks/cwe-610/evidence/vulnerable/
attacks/cwe-610/evidence/mitigated/
```

The GitHub Actions workflow `.github/workflows/cwe-610-snapshot.yml` creates run-local immutable tags for the two commits, runs the same harness over both snapshots, and uploads both evidence directories as a workflow artifact.

The primary comparison is therefore tied to explicit Git commits rather than the caller's current checkout.

## Scope and residual considerations

The experiment proves normalized lexical containment and correct validation order for the demonstrated inputs. Deployments in which the gallery tree can contain attacker-controlled symbolic links should additionally define and enforce a symlink policy, for example by resolving trusted real paths or preventing untrusted link creation. That is separate from the basic CWE-610 scenario reproduced here.
