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

## Deferred — JB savedstate/lifecycle iOS klib publication bug

JB's published `org.jetbrains.androidx.{lifecycle,savedstate}:*` parent
modules have broken iOS variants — `iosArm64ApiElements-published` ships
with empty `files`, no `available-at` to the per-target sub-module
(which IS published with a real klib), and a single redirect dep on the
Google `androidx.*` coord (which has no iOS klib). K/N consumers fail
with `KLIB resolver: Could not find org.jetbrains.androidx.*`. Confirmed
present in 2.10.0 stable, 2.11.0-alpha03, and `2.11.0-beta01+dev4086`
(jb-main HEAD) — bug NOT fixed upstream as of 2026-05-02. CMP-9502
introduced Gradle capability rules but did not address the empty-iOS-
variant pattern.

Root cause sits in `JetBrainsAndroidXRedirectingPublicationHelpers.kt`:
the `kotlinMultiplatform` publication is disabled and replaced with
`kotlinMultiplatformDecorated` built from `CustomRootComponent`.
`CustomRootComponent.getUsages()` preserves the original iOS usages,
but Gradle's Maven publish writes them without the `available-at`
redirect that the standard MPP plugin would normally inject (that
behaviour is special-cased only for the `kotlinMultiplatform` software
component).

Workaround in stforestkit (commit reference, not in this fork): force
`savedstate:savedstate:1.2.2` (last version with a working iOS klib at
the parent coord) + ship empty stub klibs for
`lifecycle-viewmodel-savedstate` matching its `unique_name`. Works,
~30 lines.

To fix in this fork (deferred until we rebase on JB 1.11 stable —
re-verify upstream first, may be fixed by then):
1. Patch `CustomRootComponent.getUsages()` to attach `available-at`
   info on non-android target usages, pointing at the per-target
   sub-module coord we already publish.
2. Drop `-Pjetbrains.publication.libraries=COMPOSE` from the CI
   workflow so SAVEDSTATE/LIFECYCLE publish too.
3. Set their respective `jetbrains.publication.version.*` properties
   to fork-suffixed versions.
4. Stforestkit then deletes its stub subproject + version-force +
   adds the fork repo with `includeGroupAndSubgroups` for the
   `org.jetbrains.androidx.{lifecycle,savedstate}` groups.

Estimated effort: ~half day for the publication patch (touches
non-trivial buildSrc internals) + ~30min for CI/wiring + 1hr CI bake
per release. Reward small (stforestkit cleanup ~30 lines) — defer
unless upstream regresses or stub workaround breaks.
