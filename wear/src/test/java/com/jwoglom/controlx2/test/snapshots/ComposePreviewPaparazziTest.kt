package com.jwoglom.controlx2.test.snapshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class attributes {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.WEAR_OS_SMALL_ROUND,
        showSystemUi = false
    )

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_CustomTimeTextKt_PreviewCustomTimeText_1_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_components_CustomTimeTextKt/PreviewCustomTimeText_1") {
                com.jwoglom.controlx2.presentation.components.PreviewCustomTimeText()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping PreviewCustomTimeText: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping PreviewCustomTimeText: " + e.message)
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_CustomTimeTextKt_PreviewCustomTimeText_2_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_components_CustomTimeTextKt/PreviewCustomTimeText_2") {
                com.jwoglom.controlx2.presentation.components.PreviewCustomTimeText()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping PreviewCustomTimeText: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping PreviewCustomTimeText: " + e.message)
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_CustomTimeTextKt_PreviewCustomTimeText_3_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_components_CustomTimeTextKt/PreviewCustomTimeText_3") {
                com.jwoglom.controlx2.presentation.components.PreviewCustomTimeText()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping PreviewCustomTimeText: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping PreviewCustomTimeText: " + e.message)
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_TopTextKt_PreviewTopText_1_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_components_TopTextKt/PreviewTopText_1") {
                com.jwoglom.controlx2.presentation.components.PreviewTopText()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping PreviewTopText: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping PreviewTopText: " + e.message)
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_TopTextKt_PreviewTopText_2_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_components_TopTextKt/PreviewTopText_2") {
                com.jwoglom.controlx2.presentation.components.PreviewTopText()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping PreviewTopText: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping PreviewTopText: " + e.message)
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_TopTextKt_PreviewTopText_3_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_components_TopTextKt/PreviewTopText_3") {
                com.jwoglom.controlx2.presentation.components.PreviewTopText()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping PreviewTopText: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping PreviewTopText: " + e.message)
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_ui_BolusScreenKt_ConditionAcknowledgedPreview_1_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_ui_BolusScreenKt/ConditionAcknowledgedPreview_1") {
                com.jwoglom.controlx2.presentation.ui.ConditionAcknowledgedPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping ConditionAcknowledgedPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping ConditionAcknowledgedPreview: " + e.message)
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_ui_BolusScreenKt_EmptyPreview_1_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_ui_BolusScreenKt/EmptyPreview_1") {
                com.jwoglom.controlx2.presentation.ui.EmptyPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping EmptyPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping EmptyPreview: " + e.message)
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_ui_LandingScreenKt_DefaultLandingScreenPreviewCropped_1_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_ui_LandingScreenKt/DefaultLandingScreenPreviewCropped_1") {
                com.jwoglom.controlx2.presentation.ui.DefaultLandingScreenPreviewCropped()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping DefaultLandingScreenPreviewCropped: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping DefaultLandingScreenPreviewCropped: " + e.message)
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_ui_LandingScreenKt_DefaultLandingScreenPreviewFull_1_() {
        try {
            paparazzi.snapshot(name = "com_jwoglom_controlx2_presentation_ui_LandingScreenKt/DefaultLandingScreenPreviewFull_1") {
                com.jwoglom.controlx2.presentation.ui.DefaultLandingScreenPreviewFull()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping DefaultLandingScreenPreviewFull: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping DefaultLandingScreenPreviewFull: " + e.message)
        }
    }

}
