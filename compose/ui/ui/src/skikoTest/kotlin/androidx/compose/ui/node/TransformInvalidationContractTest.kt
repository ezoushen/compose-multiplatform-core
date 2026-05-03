/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package androidx.compose.ui.node

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the structural-vs-content split's known limitation: a draw-only
 * invalidation (e.g. an OwnedLayer.invalidate() driven by a non-layout
 * graphics-layer property mutation) does not bust the picture-cache record.
 * If a future patch makes draw-only invalidations bust the record, this test
 * fails loud and forces an explicit reconsideration.
 */
class TransformInvalidationContractTest {

    @Test
    fun requestDrawAlone_doesNotImplyRecordInvalidation() {
        val tracker = SnapshotInvalidationTracker()
        tracker.onMeasureAndLayout()
        tracker.onDraw()
        assertFalse(tracker.hasRecordInvalidations)

        tracker.requestDraw()

        assertTrue(tracker.hasInvalidations)
        assertFalse(
            tracker.hasRecordInvalidations,
            "requestDraw is content-only by design (see KNOWN_LIMITATIONS.md). " +
                "Changing this without a corresponding fix to OwnedLayerManager.invalidate " +
                "would lose the picture-cache hit rate.",
        )
    }
}
