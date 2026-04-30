package androidx.compose.ui.window

/**
 * Phase E (perf): extension point for code that needs to encode Metal commands onto the
 * presentation command buffer AFTER Compose has flushed its Picture replay but BEFORE the
 * drawable is scheduled for present.
 *
 * Single-listener: callers reset the hook on dispose. Concurrent registration is not expected
 * — both the redrawer and the hook installer run on the main thread.
 *
 * Hook arguments:
 *   - cmdBufPtr: raw `id<MTLCommandBuffer>` address (from `objcPtr().toLong()`).
 *     The buffer is uncommitted; the hook may add encoders, then return.
 *   - drawableTexPtr: raw `id<MTLTexture>` address of the drawable's texture.
 *
 * Used by rivecmp's overlay-blit path to stamp the rive frame onto the Compose drawable
 * without going through Skia.
 */
public object MetalPostDrawHook {
    public var hook: ((cmdBufPtr: Long, drawableTexPtr: Long) -> Unit)? = null
}

/**
 * Phase E (perf): bridge for external animators (e.g. rivecmp's display ring) to wake the
 * Compose Metal redrawer WITHOUT marking the scene dirty.
 *
 * Use case: rive ticks at vsync but the Compose scene is unchanged. Calling [requestRedraw]
 * schedules a MetalRedrawer.draw on the next vsync. The redrawer's Phase D Picture cache
 * hits (scene clean), avoiding recompose + record cost. The post-draw hook then runs the
 * overlay blit.
 *
 * The active redrawer publishes its `setNeedsRedraw` here at construction. Single-redrawer
 * assumption holds for the mainstream Compose-on-iOS topology (one MetalRedrawer per scene).
 */
public object MetalRedrawTrigger {
    public var requestRedraw: (() -> Unit)? = null
}
