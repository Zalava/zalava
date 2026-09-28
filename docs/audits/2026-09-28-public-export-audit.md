# Public export audit — 2026-09-28

## Candidate and decision

This record audits the `curated/zalava` clean-tree candidate only. It is a
documentation and contributor-governance seed, not a repository visibility
change, artifact publication, or release approval. The candidate has no Git
directory, workflow run history, Pages output, release assets, or package
artifacts; those categories are absent rather than assumed safe.

The historical sources and their Git histories remain outside this candidate.
They must never be made public in place. A future public repository receives a
new clean history after its own export audit passes.

## Checks completed

| Surface | Evidence | Result |
| --- | --- | --- |
| Content boundary | `scripts/verify-zalava-public-boundary.sh curated/zalava` | Required product guidance is present and private-maintainer references are rejected. |
| Credentials and local data | `scripts/verify-zalava-public-export-audit.sh curated/zalava` | Pattern scan rejects common token/private-key forms, local paths, and maintainer-only names. |
| Assets and generated output | Same audit command | JAR, archive, and class files are rejected. |
| History | Same audit command | A `.git` directory is rejected, preventing accidental historical-source publication. |
| License and policies | Required-file checks in the audit command | Complete Apache-2.0 text, copyright notice, security, support, conduct, issue, and pull-request guidance are present. |
| Provenance | `docs/provenance.md` | Defines the publication evidence required for a later generated tree and binary. |

## Deferred, mandatory repository-specific evidence

No public target repository exists at this point. Before any target becomes
public, its owner must attach a fresh audit record covering the generated tree,
the target's clean Git history, workflows and their logs/artifacts, Pages,
release assets, package/container visibility, branch protections, attribution,
and any URLs or fixtures introduced by that export. The same evidence is
required independently for the module index and each official module; this
core-candidate result does not approve those repositories.

The release owner must run the audit command against the exact generated tree,
record its digest and source revision, and obtain review before changing
visibility. A failed scan blocks publication until the candidate is regenerated
or the finding is removed.
