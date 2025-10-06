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
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_components_CustomTimeTextKt_PreviewCustomTimeText_1_") {
            com.jwoglom.controlx2.presentation.components.PreviewCustomTimeText()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_CustomTimeTextKt_PreviewCustomTimeText_2_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_components_CustomTimeTextKt_PreviewCustomTimeText_2_") {
            com.jwoglom.controlx2.presentation.components.PreviewCustomTimeText()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_CustomTimeTextKt_PreviewCustomTimeText_3_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_components_CustomTimeTextKt_PreviewCustomTimeText_3_") {
            com.jwoglom.controlx2.presentation.components.PreviewCustomTimeText()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_TopTextKt_PreviewTopText_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_components_TopTextKt_PreviewTopText_1_") {
            com.jwoglom.controlx2.presentation.components.PreviewTopText()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_TopTextKt_PreviewTopText_2_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_components_TopTextKt_PreviewTopText_2_") {
            com.jwoglom.controlx2.presentation.components.PreviewTopText()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_components_TopTextKt_PreviewTopText_3_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_components_TopTextKt_PreviewTopText_3_") {
            com.jwoglom.controlx2.presentation.components.PreviewTopText()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_ui_BolusScreenKt_ConditionAcknowledgedPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_ui_BolusScreenKt_ConditionAcknowledgedPreview_1_") {
            com.jwoglom.controlx2.presentation.ui.ConditionAcknowledgedPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_ui_BolusScreenKt_EmptyPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_ui_BolusScreenKt_EmptyPreview_1_") {
            com.jwoglom.controlx2.presentation.ui.EmptyPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_ui_LandingScreenKt_DefaultLandingScreenPreviewCropped_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_ui_LandingScreenKt_DefaultLandingScreenPreviewCropped_1_") {
            com.jwoglom.controlx2.presentation.ui.DefaultLandingScreenPreviewCropped()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_ui_LandingScreenKt_DefaultLandingScreenPreviewFull_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_ui_LandingScreenKt_DefaultLandingScreenPreviewFull_1_") {
            com.jwoglom.controlx2.presentation.ui.DefaultLandingScreenPreviewFull()
        }
    }

}
