package com.jwoglom.controlx2.test.snapshots

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ComposePreviewSnapshotTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Landing screens
    @Test fun snapshot_DefaultLandingScreenPreviewFull() = snapshot { com.jwoglom.controlx2.presentation.ui.DefaultLandingScreenPreviewFull() }
    @Test fun snapshot_DefaultLandingScreenPreviewCropped() = snapshot { com.jwoglom.controlx2.presentation.ui.DefaultLandingScreenPreviewCropped() }

    // Bolus screen
    @Test fun snapshot_EmptyPreview() = snapshot { com.jwoglom.controlx2.presentation.ui.EmptyPreview() }
    @Test fun snapshot_ConditionAcknowledgedPreview() = snapshot { com.jwoglom.controlx2.presentation.ui.ConditionAcknowledgedPreview() }

    // Components
    @Test fun snapshot_PreviewCustomTimeText() = snapshot { com.jwoglom.controlx2.presentation.components.PreviewCustomTimeText() }
    @Test fun snapshot_PreviewTopText() = snapshot { com.jwoglom.controlx2.presentation.components.PreviewTopText() }

    private fun snapshot(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.setContent { content() }
        composeTestRule.onRoot().captureRoboImage()
    }
}
