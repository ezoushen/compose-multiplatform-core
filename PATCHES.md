# picture-cache fork — patch index

This fork adds a small, self-contained set of patches to JetBrains Compose
Multiplatform that cut Skia replay cost on iOS by caching the outer
`SkPicture` across display-link ticks and skipping per-tick replay when
the recorded scene has not structurally changed.

It is rebased onto upstream `jb-main` rather than merged, so each patch
remains an atomic, individually portable commit. To move to a newer CMP
release, rebase this branch on top of the new upstream tag.

## Base

- Upstream: `JetBrains/compose-multiplatform-core`, branch `jb-main`
- Currently rebased on tag commit `33b267bdaf4` ("Copy Jetpack Compose
  1.10.3"). When upstream cuts 1.10.4 / 1.11.0, rebase on that commit.

## Patches (in order, oldest first)

| # | Commit subject | Touches |
|---|----------------|---------|
| 1 | perf(ui): elide BroadcastFrameClock.sendFrame when idle | `compose:runtime` (Recomposer, BroadcastFrameClock) |
| 2 | feat(ui): add ComposeScene.sceneDirty / markSceneClean fast-path hint | `compose:ui` (BaseComposeScene, public API surface) |
| 3 | feat(ios): cache SkPicture across display-link ticks + post-draw hook for external compositors | `compose:ui` iOS — MetalRedrawer, ComposeMetalContext |
| 4 | perf(ios): cache BackendRenderTarget+Surface per drawable texture | `compose:ui` iOS — MetalRedrawer drawableVersions cache |
| 5 | perf(ios): skip Skia replay when drawable already at current picture version | `compose:ui` iOS — MetalRedrawer |
| 6 | feat(ios): expose DirectContext via ComposeMetalContext for external Skia integrations | `compose:ui` iOS public surface |
| 7 | feat(ios): remove Phase E external-compositor hooks | `compose:ui` iOS — cleanup of unused exports |
| 8 | perf(ui): split structural vs content invalidations in BaseComposeScene | `compose:ui` — SnapshotInvalidationTracker, RootNodeOwner, BaseComposeScene |
Patches 1, 2, 8 touch shared `compose:ui` / `compose:runtime` files;
those are the most likely to conflict on rebase. Patches 3–7 live in
iOS-specific files (MetalRedrawer, ComposeMetalContext) that change
much more rarely upstream.

The version of the published COMPOSE artifacts is controlled at CI
time via the existing `-Pjetbrains.publication.version.COMPOSE=...`
property (see `JetBrainsVersionsService`). No fork-side patch needed
for versioning.

## Rebase recipe

```bash
# Update upstream
git fetch origin jb-main

# Find the new upstream cut commit (the "Copy Jetpack Compose X.Y.Z" commit)
NEW_BASE=$(git log origin/jb-main --grep="^Copy Jetpack Compose " -n 1 --pretty=%H)

# Rebase, keeping commits as-is
git rebase --onto "$NEW_BASE" 33b267bdaf4 feat/picture-cache-1.10.3

# Resolve any conflicts (patches 1, 2, 8 are the candidates), test, then:
git tag picture-cache-v<new-version>.1
git push origin feat/picture-cache-1.10.3 --force-with-lease
git push origin picture-cache-v<new-version>.1
```

The CI workflow (`.github/workflows/publish-picture-cache.yml`) takes
the tag suffix as the version extra, so a tag like
`picture-cache-v1.11.0.1` publishes `1.11.0-picture-cache.1`.

## Building locally

```bash
SNAPSHOT=1 ./gradlew publishComposeJbToMavenLocal \
  -Pcompose.platforms=macos,uikit,jvm,android
```

publishes `1.11.0-SNAPSHOT` to `~/.m2/repository`.

To match what CI publishes:

```bash
./gradlew publishComposeJbToMavenLocal \
  -Pcompose.platforms=macos,uikit,jvm,android \
  -Pcompose.versionExtra=-picture-cache.local
```
