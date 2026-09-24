# Proposed Android package cutover

**Not active yet.** Publication configuration is prepared in source. Existing GitHub
packages are still present. No deletion or completed migration is implied by
this document.

## Version baseline and destinations

| Artifact | Current main baseline | GitHub Packages repository |
| --- | --- | --- |
| `ai.metabind:bindjs-android` | `0.0.33` | `metabindai/bindjs-android` |
| `ai.metabind:metabindai-android` | `0.2.12` | `metabindai/metabind-android` |
| `ai.metabind:mcpappshost-android` | `0.2.12` | `metabindai/metabind-android` |
| `ai.metabind:metabind-content-android` | `0.2.12` | `metabindai/metabind-android` |

These version pins follow main; they are not replacement release candidates.
The new preview APIs require a new SDK release. Choose unused release versions
and update consumers during the cutover rather than overwriting existing releases.

As verified September 18, 2026, the old `bindjs-android-binary` repository holds these four packages plus the
retired `ai.metabind:metabind-android` and
`ai.metabind:metabind-assistant-android` packages. A complete retirement must
account for all six; moving only BindJS leaves the SDK distribution behind.

GitHub rejected publishing the existing BindJS package through a different
repository: its Maven package name is already associated with the old repo.
The normal unlink/relink operation does not apply to Maven packages. Verify the
delete/recreate procedure before approving a cutover; changing URLs alone is
not a package migration.

## What a clean break affects

Deleting historical versions breaks fresh builds pinned to them, directly or
through an older SDK. A warm Gradle cache can temporarily hide the failure.
Already-installed apps continue working because the libraries are packaged
inside them. Historical checkouts will need dependency changes to build again.
Public download counts cannot identify every external consumer.

## Consumer changes

1. Configure both authenticated Maven repositories:
   `https://maven.pkg.github.com/metabindai/bindjs-android` and
   `https://maven.pkg.github.com/metabindai/metabind-android`.
2. Update BindJS to the announced replacement version. SDK users should upgrade
   to an SDK release that pins the available BindJS version; a URL change alone
   cannot fix an SDK that requests a deleted version.
3. Replace the retired `ai.metabind:metabind-android` dependency with
   `ai.metabind:metabind-content-android`. Replace the retired
   `ai.metabind:metabind-assistant-android` dependency with
   `ai.metabind:metabindai-android`, updating Kotlin imports and APIs as needed.
4. Keep package credentials outside source control. Public GitHub Maven packages
   still need a token with `read:packages`, or an appropriately authorized
   GitHub Actions token.
5. After the release announcement, build and test on a fresh CI worker without a
   local source or Maven override. Do not merge candidate dependency pins until
   all required replacement packages are downloadable.

## Maintainer cutover

Prepare and validate release artifacts, consumer PRs, and this guide first.
Record exact versions and hashes before any removal. Announce which old builds
will stop working, then obtain explicit approval for deleting historical
packages. Package read/write authorization does not authorize deletion.

Once the cutover is approved, follow the verified registry migration procedure,
publish BindJS first and the SDKs second, and verify downloads and clean consumer
builds. Mark this guide active only after verification. The old repository can
then retain an archived migration notice with links to the source repositories.

Deleting and recreating a package is not a guaranteed rollback path: GitHub's
normal restoration requires the namespace still to be available. See
[GitHub's deletion and restoration rules](https://docs.github.com/en/packages/learn-github-packages/deleting-and-restoring-a-package).
