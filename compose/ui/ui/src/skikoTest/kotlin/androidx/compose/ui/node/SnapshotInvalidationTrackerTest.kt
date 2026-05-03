/*
 * Copyright 2026 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package androidx.compose.ui.node

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotInvalidationTrackerTest {

    @Test
    fun newTracker_startsWithBothInvalidationFlagsTrue() {
        val tracker = SnapshotInvalidationTracker()
        assertTrue(tracker.hasInvalidations, "fresh tracker should have invalidations")
        assertTrue(tracker.hasRecordInvalidations, "fresh tracker should require record")
    }

    @Test
    fun requestRecord_setsBothRecordAndDraw() {
        val tracker = SnapshotInvalidationTracker()
        tracker.onMeasureAndLayout()
        tracker.onDraw()
        assertFalse(tracker.hasInvalidations)
        assertFalse(tracker.hasRecordInvalidations)

        tracker.requestRecord()

        assertTrue(tracker.hasInvalidations, "requestRecord must imply hasInvalidations")
        assertTrue(tracker.hasRecordInvalidations, "requestRecord must imply hasRecordInvalidations")
    }

    @Test
    fun requestDraw_setsDrawButNotRecord() {
        val tracker = SnapshotInvalidationTracker()
        tracker.onMeasureAndLayout()
        tracker.onDraw()

        tracker.requestDraw()

        assertTrue(tracker.hasInvalidations, "requestDraw must imply hasInvalidations")
        assertFalse(
            tracker.hasRecordInvalidations,
            "requestDraw is content-only and must not imply hasRecordInvalidations",
        )
    }

    @Test
    fun requestMeasureAndLayout_setsBothRecordAndDraw() {
        val tracker = SnapshotInvalidationTracker()
        tracker.onMeasureAndLayout()
        tracker.onDraw()

        tracker.requestMeasureAndLayout()

        assertTrue(tracker.hasInvalidations)
        assertTrue(
            tracker.hasRecordInvalidations,
            "layout invalidation forces a record bust (transforms baked at record time)",
        )
    }

    @Test
    fun onDraw_clearsBothRecordAndDraw() {
        val tracker = SnapshotInvalidationTracker()
        tracker.requestRecord()
        tracker.onMeasureAndLayout()
        tracker.onDraw()

        assertFalse(tracker.hasRecordInvalidations)
    }
}
