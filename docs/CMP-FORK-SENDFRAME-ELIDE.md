# CMP Fork: Elide `BroadcastFrameClock.sendFrame` When Idle

Branch: `feat/sendframe-elide` (off jb-main HEAD).
Phase: E of `perf-optimization-stack` (see
`/Users/ezou/Desktop/seekrtech/rive-cmp/.worktrees/render-decouple/docs/superpowers/plans/2026-04-29-perf-optimization-stack.md`).

## What changed

Single file: `compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/scene/BaseComposeScene.skiko.kt`.

`BaseComposeScene.recompose(nanoTime)` is invoked from both `render(...)` and
`recomposeAndLayout(...)`. The last step of `recompose` was an unconditional
`frameClock.sendFrame(nanoTime)`, which:

1. Resumes every coroutine suspended in `withFrameNanos` / `withFrameMillis`
   on this clock.
2. Triggers the `Recomposer`'s frame cycle (drives animation tick + applies
   pending invalidations).

When neither (1) nor (2) has anything to do — the steady state for a Rive
playback view that owns its own animation loop and produces no Compose
invalidations — this call is pure overhead on every CADisplayLink tick.
Earlier `sample` profiling on iOS attributed ~1 percentage point of main
thread CPU to it.

The fix gates the call on two public signals already used elsewhere in the
file (`hasPendingDraws` / `hasInvalidations`):

```kotlin
if (frameClock.hasAwaiters || recomposer.hasPendingWork) {
    frameClock.sendFrame(nanoTime)
}
```

- `BroadcastFrameClock.hasAwaiters` (public val) — true while any coroutine is
  parked in `withFrameNanos` / `withFrameMillis` on this clock.
- `ComposeSceneRecomposer.hasPendingWork` (internal-to-CMP wrapper, public to
  this module) — composite of `Recomposer.hasPendingWork ||
  effectDispatcher.hasImmediateTasks() || recomposeDispatcher.hasImmediateTasks()`.
  Covers pending recompositions, snapshot invalidations queued for commit, and
  effect/recompose dispatcher work.

Both `recomposer.performScheduledEffects()` and
`recomposer.performScheduledRecomposerTasks()` already ran immediately above
the gate, so the check reflects the post-drain state.

## Why this is safe

`sendFrame` only has observable effect when somebody is waiting for it:

- A `withFrameNanos` awaiter shows up in `hasAwaiters` the moment it suspends.
- A pending recomposition / pending snapshot apply / queued effect shows up
  in one of the three components of `hasPendingWork`.

If both are false there is no consumer of the frame nanos value and no work
for the Recomposer cycle to perform. Skipping is equivalent to "tick the
clock but nobody listened".

The same gating composition is already trusted by:
- `BaseComposeScene.updateInvalidations()` — uses `frameClock.hasAwaiters` to
  decide whether to mark the scene as having pending draws.
- `BaseComposeScene.hasInvalidations()` — combines `hasPendingDraws` and
  `recomposer.hasPendingWork` to expose whether the scene needs another
  render pass.

## Risk

False-negative gating freezes animations. Mitigations:

- Both signals are checked AFTER draining effects / scheduled recomposer
  tasks, so any work those drains *enqueued* on the clock or recomposer is
  visible.
- `BroadcastFrameClock.sendFrame` is also called from
  `frameClock` consumers themselves only via the awaiter resume path — there
  is no other side effect of "sending a frame" that we lose by skipping.
- If a regression appears (e.g. infinite-animation API like
  `rememberInfiniteTransition` not advancing), revert by deleting the
  if-guard. Single line of code.

### Test plan to validate (manual)

1. Compose `LaunchedEffect { while(true) { withFrameNanos { ... } } }` —
   verify the block fires every frame. (Hits `hasAwaiters` path.)
2. `rememberInfiniteTransition()` driving an `animateFloat` — verify it ticks
   smoothly. (Hits `hasPendingWork` via Recomposer's animation subscription.)
3. `LaunchedEffect { delay(1000); state.value = ... }` — verify state change
   triggers recomposition without manual input. (Hits `hasPendingWork` after
   snapshot apply.)
4. Steady-state Rive playback with no other Compose state changes — verify
   `sendFrame` no longer dominates the iOS main thread sample profile. This
   is the optimization target.
5. Touch input during animation — verify pointer events still drive
   recomposition (`postponeInvalidation` path is untouched).

## Build + publish

```bash
cd /Users/ezou/Desktop/seekrtech/rive-cmp/upstream/cmp-sendframe-elide
./gradlew :compose:ui:ui:compileKotlinIosSimulatorArm64
./gradlew publishComposeJbPublicationToMavenLocal   # optional, full publish
```

For per-module publish (faster), publish only the modules that change:
- `:compose:ui:ui` — only file touched.

The fork version reports as `1.11.10-alpha01+devXXXX`. rive-cmp currently
pins CMP `1.10.3`; the modified API surface (`BaseComposeScene.render`,
`Recomposer.hasPendingWork`, `BroadcastFrameClock.hasAwaiters`) is stable
across these versions, so the change can be back-ported by cherry-pick if a
1.10.x fork is needed instead of bumping consumers.

### GAV consumers should pin

After mavenLocal publish, the affected coordinate is:

```
org.jetbrains.compose.ui:ui-uikit:<fork-version>
org.jetbrains.compose.ui:ui:<fork-version>
```

(`BaseComposeScene` is in the common skiko source set, so all skiko targets
pick up the change — including desktop. Consumer apps need to resolve
`mavenLocal()` first in their repository list.)

## Coordination with Phase D

Phase D (`/Users/ezou/Desktop/seekrtech/rive-cmp/upstream/cmp-picture-cache`,
branch `feat/picture-cache`) also edits `BaseComposeScene.skiko.kt`. To keep
the merge clean:

- Phase E touches only `recompose(nanoTime)` (the helper called from
  `render`). The `render(canvas, nanoTime)` method itself is unchanged.
- Phase D is expected to modify the body of `render(...)` (picture caching
  around the `draw(canvas)` call). The two changes do not overlap textually.
- Suggested merge order: Phase E first (smaller, lower risk), then Phase D
  rebases on top.

## Files touched

- `compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/scene/BaseComposeScene.skiko.kt`
- `docs/CMP-FORK-SENDFRAME-ELIDE.md` (this file)
