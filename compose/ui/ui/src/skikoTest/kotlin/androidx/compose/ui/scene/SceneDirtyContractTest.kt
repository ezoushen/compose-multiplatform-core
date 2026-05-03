/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package androidx.compose.ui.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Surface

class SceneDirtyContractTest {

    private fun newScene() = PlatformLayersComposeScene(size = IntSize(100, 100))

    private fun composeCanvas() =
        Surface.makeRasterN32Premul(100, 100).canvas.asComposeCanvas()

    @Test
    fun freshScene_sceneDirtyIsTrue() = runTest(StandardTestDispatcher()) {
        val scene = newScene()
        try {
            assertTrue(
                scene.sceneDirty,
                "fresh scene must be dirty so the first frame records",
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun markSceneClean_clearsFlag() = runTest(StandardTestDispatcher()) {
        val scene = newScene()
        try {
            scene.setContent { Box(Modifier.fillMaxSize()) }
            scene.render(composeCanvas(), nanoTime = 0L)
            scene.markSceneClean()
            assertFalse(
                scene.sceneDirty,
                "markSceneClean must clear the dirty flag",
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun freshThenSetContent_sceneDirtyRemainsTrue() = runTest(StandardTestDispatcher()) {
        val scene = newScene()
        try {
            scene.setContent { Box(Modifier.fillMaxSize()) }
            assertTrue(
                scene.sceneDirty,
                "setContent before any render must leave the scene dirty",
            )
        } finally {
            scene.close()
        }
    }
}
