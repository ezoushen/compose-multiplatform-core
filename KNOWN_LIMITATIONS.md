# Known limitations of the picture-cache fork

## Transform/clip/alpha mutations bypassing layout

`OwnedLayerManager.invalidate()` routes to `SnapshotInvalidationTracker.requestDraw()`,
which is content-only — it does not bust the cached SkPicture. The Picture's
`drawRenderNode` commands embed transforms (translate/scale/rotate/clip/alpha) that
were resolved at record time.

If a `GraphicsLayer` property (e.g. `scaleX`, `translationX`, `alpha`, `clipRect`)
is mutated via a snapshot state read **without going through a layout pass**, the
cached Picture replays with stale transforms until the next layout-invalidating
event.

In practice this does not fire for idiomatic Compose code: layer properties are
applied via `Modifier.graphicsLayer { ... }` whose state reads inside the modifier's
`onMeasure` / `onPlace` callbacks trigger `requestRecord` via `RootNodeOwner`. The
limitation only matters if a consumer is writing layer properties through the
raw `GraphicsLayer` API outside any layout pass — uncommon.

If this turns out to be hit in practice, the fix is to discriminate transform-only
invalidations from content-only invalidations at the `OwnedLayer.invalidate()` call
site (~50 LOC API change). Not done here because no consumer in our workload trips it.

## `_sceneDirty` narrow read/write race

Between `MetalRedrawer.draw()` reading `isSceneDirty()` and the patched
`markSceneClean()` write (now ahead of `render()`), a frame-clock-thread
`BaseComposeScene.updateInvalidations` can write `_sceneDirty=true` and have it
lost. Closing the window requires upgrading `_sceneDirty` from `@Volatile var` to
`kotlinx.atomicfu.AtomicBoolean.compareAndSet`. Not done because the resulting
missed dirty would, in the worst case, defer a record by one frame — not produce
a stuck UI (the next dirty event re-sets the flag). Adding the atomic is a
follow-up if observed in practice.
