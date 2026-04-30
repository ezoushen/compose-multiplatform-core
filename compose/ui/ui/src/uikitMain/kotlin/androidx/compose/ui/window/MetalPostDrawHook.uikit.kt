package androidx.compose.ui.window

/**
 * Extension point for code that needs to encode Metal commands onto the presentation command
 * buffer AFTER Compose has flushed its Picture replay but BEFORE the drawable is scheduled for
 * present. This lets an external Metal compositor (e.g. an overlay-blit pipeline) stamp content
 * onto the Compose drawable without going through Skia.
 *
 * Single-listener: callers reset the hook on dispose. Concurrent registration is not expected
 * — both the redrawer and the hook installer run on the main thread.
 *
 * Hook arguments:
 *   - cmdBufPtr: raw `id<MTLCommandBuffer>` address (from `objcPtr().toLong()`).
 *     The buffer is uncommitted; the hook may add encoders, then return.
 *   - drawableTexPtr: raw `id<MTLTexture>` address of the drawable's texture.
 */
public object MetalPostDrawHook {
    public var hook: ((cmdBufPtr: Long, drawableTexPtr: Long) -> Unit)? = null
}

/**
 * Bridge for external animators to wake the Compose Metal redrawer WITHOUT marking the scene
 * dirty.
 *
 * Use case: an external animator ticks at vsync but the Compose scene is unchanged. Calling
 * [requestRedraw] schedules a MetalRedrawer.draw on the next vsync. The redrawer's picture
 * cache hits (scene clean), avoiding recompose + record cost. The post-draw hook then runs the
 * external compositor's encode step.
 *
 * The active redrawer publishes its `setNeedsRedraw` here at construction. Single-redrawer
 * assumption holds for the mainstream Compose-on-iOS topology (one MetalRedrawer per scene).
 */
public object MetalRedrawTrigger {
    public var requestRedraw: (() -> Unit)? = null
}
