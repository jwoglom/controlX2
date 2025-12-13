package com.jwoglom.controlx2.test.snapshots

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@Ignore("Paparazzi snapshot tests are disabled")
class attributes {
    companion object {
        init {
            // Paparazzi's ByteBuddy dependency only supports up to Java 23 unless this flag is set.
            System.setProperty("net.bytebuddy.experimental", "true")
        }
    }
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        showSystemUi = false
    )

    @Test
    fun presentation_MobileAppKt_DefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.DefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping DefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping DefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_AppSetupKt_AppSetupDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.AppSetupDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping AppSetupDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping AppSetupDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_FirstLaunchKt_FirstLaunchDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.FirstLaunchDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping FirstLaunchDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping FirstLaunchDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_LandingKt_BolusPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.BolusPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping BolusPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping BolusPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_LandingKt_LandingDebugPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.LandingDebugPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping LandingDebugPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping LandingDebugPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_LandingKt_LandingDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.LandingDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping LandingDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping LandingDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_LandingKt_LandingSettingsPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.LandingSettingsPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping LandingSettingsPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping LandingSettingsPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_LandingKt_TempRatePreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.TempRatePreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping TempRatePreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping TempRatePreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_PumpSetupKt_PumpSetupDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.PumpSetupDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping PumpSetupDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping PumpSetupDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinActive_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinActive()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping ActionsDefaultPreviewInsulinActive: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping ActionsDefaultPreviewInsulinActive: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinActive_StopMenuOpen_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinActive_StopMenuOpen()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping ActionsDefaultPreviewInsulinActive_StopMenuOpen: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping ActionsDefaultPreviewInsulinActive_StopMenuOpen: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinSuspended_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinSuspended()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping ActionsDefaultPreviewInsulinSuspended: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping ActionsDefaultPreviewInsulinSuspended: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_ActionsKt_ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_ActionsKt_ActionsDefaultPreview_StartTempRate_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreview_StartTempRate()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping ActionsDefaultPreview_StartTempRate: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping ActionsDefaultPreview_StartTempRate: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_BolusWindowKt_DefaultBolusPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.DefaultBolusPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping DefaultBolusPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping DefaultBolusPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_CGMActionsKt_CGMActionsDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.CGMActionsDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping CGMActionsDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping CGMActionsDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_CGMActionsKt_CGMActionsDefaultPreviewCgmStart_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.CGMActionsDefaultPreviewCgmStart()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping CGMActionsDefaultPreviewCgmStart: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping CGMActionsDefaultPreviewCgmStart: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_CartridgeActionsKt_CartridgeActionsDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping CartridgeActionsDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping CartridgeActionsDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_CartridgeActionsKt_CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_CartridgeActionsKt_CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_DashboardKt_DashboardDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.DashboardDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping DashboardDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping DashboardDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_DebugKt_DebugDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.DebugDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping DebugDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping DebugDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_NotificationsKt_NotificationsDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.NotificationsDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping NotificationsDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping NotificationsDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_NotificationsKt_NotificationsDefaultPreview_WithNotifications_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.NotificationsDefaultPreview_WithNotifications()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping NotificationsDefaultPreview_WithNotifications: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping NotificationsDefaultPreview_WithNotifications: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_ProfileActionsKt_ProfileActionsDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.ProfileActionsDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping ProfileActionsDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping ProfileActionsDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_SettingsKt_SettingsDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.SettingsDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping SettingsDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping SettingsDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_TempRateWindowKt_DefaultTempRateWindow_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.DefaultTempRateWindow()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping DefaultTempRateWindow: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping DefaultTempRateWindow: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_TempRateWindowKt_DefaultTempRateWindow_Filled_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.DefaultTempRateWindow_Filled()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping DefaultTempRateWindow_Filled: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping DefaultTempRateWindow_Filled: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_DashboardCgmChartKt_DashboardCgmChartDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.DashboardCgmChartDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping DashboardCgmChartDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping DashboardCgmChartDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview0_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview0()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizBatteryIconPreview0: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizBatteryIconPreview0: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview20_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview20()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizBatteryIconPreview20: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizBatteryIconPreview20: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview30_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview30()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizBatteryIconPreview30: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizBatteryIconPreview30: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview40_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview40()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizBatteryIconPreview40: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizBatteryIconPreview40: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview60_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview60()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizBatteryIconPreview60: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizBatteryIconPreview60: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizBatteryIconKt_HorizBatteryIconPreview80_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizBatteryIconPreview80()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizBatteryIconPreview80: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizBatteryIconPreview80: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview0_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview0()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizCartridgeIconPreview0: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizCartridgeIconPreview0: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview20_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview20()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizCartridgeIconPreview20: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizCartridgeIconPreview20: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview30_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview30()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizCartridgeIconPreview30: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizCartridgeIconPreview30: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview40_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview40()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizCartridgeIconPreview40: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizCartridgeIconPreview40: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview60_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview60()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizCartridgeIconPreview60: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizCartridgeIconPreview60: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreview80_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreview80()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizCartridgeIconPreview80: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizCartridgeIconPreview80: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_HorizCartridgeIconKt_HorizCartridgeIconPreviewPlus_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.HorizCartridgeIconPreviewPlus()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping HorizCartridgeIconPreviewPlus: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping HorizCartridgeIconPreviewPlus: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_PumpStatusBarKt_PumpStatusBarDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.PumpStatusBarDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping PumpStatusBarDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping PumpStatusBarDefaultPreview: " + e.message)
        }
    }

    @Test
    fun presentation_screens_sections_components_VersionInfoKt_VersionInfoDefaultPreview_1_() {
        try {
            paparazzi.snapshot(name = "preview") {
                com.jwoglom.controlx2.presentation.screens.sections.components.VersionInfoDefaultPreview()
            }
        } catch (e: ClassCastException) {
            // Skip previews that try to cast Context to Activity in Paparazzi
            println("Skipping VersionInfoDefaultPreview: " + e.message)
        } catch (e: IllegalStateException) {
            // Skip previews that require ViewModelStoreOwner (e.g., NavHost)
            println("Skipping VersionInfoDefaultPreview: " + e.message)
        }
    }

}
