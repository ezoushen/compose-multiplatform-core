package androidx.compose.ui.window

import kotlin.concurrent.Volatile
import org.jetbrains.skia.DirectContext

/**
 * Exposes the active [DirectContext] used by [MetalRedrawer] to render Compose
 * scenes on iOS. External compositors that integrate with Compose's Skia render
 * pipeline (e.g. drawing GPU textures into Compose's canvas via
 * [org.jetbrains.skia.Surface.makeFromBackendRenderTarget] +
 * [org.jetbrains.skia.Surface.makeImageSnapshot]) need this context to wrap
 * their Metal textures into Skia surfaces tied to the same GPU work the
 * redrawer flushes per frame.
 *
 * Single-redrawer assumption holds for the mainstream Compose-on-iOS topology
 * (one [MetalRedrawer] per scene). Last-redrawer-wins.
 *
 * The context is non-null between [MetalRedrawer]'s init and dispose. Reading
 * outside that window returns null.
 */
public object ComposeMetalContext {
    @Volatile
    public var directContext: DirectContext? = null
        internal set
}
