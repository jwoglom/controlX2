package com.jwoglom.controlx2.test.snapshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class attributes {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        showSystemUi = false
    )

    @Test
    fun test_com_jwoglom_controlx2_presentation_MobileAppKt_DefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_MobileAppKt_DefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.DefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_AppSetupKt_AppSetupDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_AppSetupKt_AppSetupDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.AppSetupDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_FirstLaunchKt_FirstLaunchDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_FirstLaunchKt_FirstLaunchDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.FirstLaunchDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_LandingKt_BolusPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_LandingKt_BolusPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.BolusPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_LandingKt_LandingDebugPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_LandingKt_LandingDebugPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.LandingDebugPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_LandingKt_LandingDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_LandingKt_LandingDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.LandingDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_LandingKt_LandingSettingsPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_LandingKt_LandingSettingsPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.LandingSettingsPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_LandingKt_TempRatePreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_LandingKt_TempRatePreview_1_") {
            com.jwoglom.controlx2.presentation.screens.TempRatePreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_PumpSetupKt_PumpSetupDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_PumpSetupKt_PumpSetupDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.PumpSetupDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinActive_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinActive_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinActive()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinActive_StopMenuOpen_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinActive_StopMenuOpen_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinActive_StopMenuOpen()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinSuspended_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinSuspended_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinSuspended()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreview_StartTempRate_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_ActionsKt_ActionsDefaultPreview_StartTempRate_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreview_StartTempRate()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_BolusWindowKt_DefaultBolusPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_BolusWindowKt_DefaultBolusPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.DefaultBolusPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_CGMActionsKt_CGMActionsDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_CGMActionsKt_CGMActionsDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.CGMActionsDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_CGMActionsKt_CGMActionsDefaultPreviewCgmStart_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_CGMActionsKt_CGMActionsDefaultPreviewCgmStart_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.CGMActionsDefaultPreviewCgmStart()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_CartridgeActionsKt_CartridgeActionsDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_CartridgeActionsKt_CartridgeActionsDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_CartridgeActionsKt_CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_CartridgeActionsKt_CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_CartridgeActionsKt_CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_CartridgeActionsKt_CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_DashboardKt_DashboardDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_DashboardKt_DashboardDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.DashboardDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_DebugKt_DebugDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_DebugKt_DebugDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.DebugDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_NotificationsKt_NotificationsDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_NotificationsKt_NotificationsDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.NotificationsDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_NotificationsKt_NotificationsDefaultPreview_WithNotifications_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_NotificationsKt_NotificationsDefaultPreview_WithNotifications_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.NotificationsDefaultPreview_WithNotifications()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_ProfileActionsKt_ProfileActionsDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_ProfileActionsKt_ProfileActionsDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.ProfileActionsDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_SettingsKt_SettingsDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_SettingsKt_SettingsDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.SettingsDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_TempRateWindowKt_DefaultTempRateWindow_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_TempRateWindowKt_DefaultTempRateWindow_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.DefaultTempRateWindow()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_TempRateWindowKt_DefaultTempRateWindow_Filled_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_TempRateWindowKt_DefaultTempRateWindow_Filled_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.DefaultTempRateWindow_Filled()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_DashboardCgmChartKt_DashboardCgmChartDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_DashboardCgmChartKt_DashboardCgmChartDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.DashboardCgmChartDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview0_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview0_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview0()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview20_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview20_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview20()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview30_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview30_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview30()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview40_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview40_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview40()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview60_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview60_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview60()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview80_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview80_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview80()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview0_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview0_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview0()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview20_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview20_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview20()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview30_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview30_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview30()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview40_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview40_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview40()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview60_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview60_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview60()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview80_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview80_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview80()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreviewPlus_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreviewPlus_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreviewPlus()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_PumpStatusBarKt_PumpStatusBarDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_PumpStatusBarKt_PumpStatusBarDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.PumpStatusBarDefaultPreview()
        }
    }

    @Test
    fun test_com_jwoglom_controlx2_presentation_screens_sections_components_VersionInfoKt_VersionInfoDefaultPreview_1_() {
        paparazzi.snapshot(name = "test_com_jwoglom_controlx2_presentation_screens_sections_components_VersionInfoKt_VersionInfoDefaultPreview_1_") {
            com.jwoglom.controlx2.presentation.screens.sections.components.VersionInfoDefaultPreview()
        }
    }

}
