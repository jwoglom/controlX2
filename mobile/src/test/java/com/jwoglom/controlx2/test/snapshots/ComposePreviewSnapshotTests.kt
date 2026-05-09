package com.jwoglom.controlx2.test.snapshots

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ComposePreviewSnapshotTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Screens
    @Test fun snapshot_DefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.DefaultPreview() }
    @Test fun snapshot_FirstLaunchDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.FirstLaunchDefaultPreview() }
    @Test fun snapshot_PumpSetupDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.PumpSetupDefaultPreview() }
    @Test fun snapshot_AppSetupDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.AppSetupDefaultPreview() }

    // Landing variants
    @Test fun snapshot_LandingDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.LandingDefaultPreview() }
    @Test fun snapshot_BolusPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.BolusPreview() }
    @Test fun snapshot_TempRatePreview() = snapshot { com.jwoglom.controlx2.presentation.screens.TempRatePreview() }
    @Test fun snapshot_LandingDebugPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.LandingDebugPreview() }
    @Test fun snapshot_LandingSettingsPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.LandingSettingsPreview() }

    // Sections
    @Test fun snapshot_DashboardDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.DashboardDefaultPreview() }
    @Test fun snapshot_ActionsDefaultPreviewInsulinActive() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinActive() }
    @Test fun snapshot_ActionsDefaultPreviewInsulinActive_StopMenuOpen() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinActive_StopMenuOpen() }
    @Test fun snapshot_ActionsDefaultPreviewInsulinSuspended() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinSuspended() }
    @Test fun snapshot_ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen() }
    @Test fun snapshot_ActionsDefaultPreview_StartTempRate() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.ActionsDefaultPreview_StartTempRate() }
    @Test fun snapshot_DefaultBolusPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.DefaultBolusPreview() }
    @Test fun snapshot_DefaultTempRateWindow() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.DefaultTempRateWindow() }
    @Test fun snapshot_DefaultTempRateWindow_Filled() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.DefaultTempRateWindow_Filled() }
    @Test fun snapshot_NotificationsDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.NotificationsDefaultPreview() }
    @Test fun snapshot_NotificationsDefaultPreview_WithNotifications() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.NotificationsDefaultPreview_WithNotifications() }
    @Test fun snapshot_ProfileActionsDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.ProfileActionsDefaultPreview() }
    @Test fun snapshot_SettingsDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.SettingsDefaultPreview() }
    @Test fun snapshot_DebugDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.DebugDefaultPreview() }
    @Test fun snapshot_CGMActionsDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.CGMActionsDefaultPreview() }
    @Ignore("Flaky under Robolectric: AppNotIdleException")
    @Test fun snapshot_CGMActionsDefaultPreviewCgmStart() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.CGMActionsDefaultPreviewCgmStart() }
    @Test fun snapshot_CartridgeActionsDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreview() }
    @Test fun snapshot_CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped() }
    @Test fun snapshot_CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped() }
    @Test fun snapshot_CartridgeActionsDefaultPreviewFillTubing_InsulinStopped() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreviewFillTubing_InsulinStopped() }
    @Test fun snapshot_CartridgeActionsDefaultPreviewFillCannula_InsulinStopped() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.CartridgeActionsDefaultPreviewFillCannula_InsulinStopped() }
    @Test fun snapshot_ControlIQSettingsActionsPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.ControlIQSettingsActionsPreview() }
    @Test fun snapshot_SafetyLimitsActionsPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.SafetyLimitsActionsPreview() }
    @Test fun snapshot_SoundSettingsActionsPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.SoundSettingsActionsPreview() }

    // Cards
    @Test fun snapshot_GlucoseHeroCardInRangePreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.GlucoseHeroCardInRangePreview() }
    @Test fun snapshot_GlucoseHeroCardHighPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.GlucoseHeroCardHighPreview() }
    @Test fun snapshot_GlucoseHeroCardLowPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.GlucoseHeroCardLowPreview() }
    @Test fun snapshot_GlucoseHeroCardElevatedPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.GlucoseHeroCardElevatedPreview() }
    @Test fun snapshot_GlucoseHeroCardNullPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.GlucoseHeroCardNullPreview() }
    @Test fun snapshot_SensorInfoCardPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.SensorInfoCardPreview() }
    @Test fun snapshot_SensorInfoCardLowPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.SensorInfoCardLowPreview() }
    @Test fun snapshot_SensorInfoCardUrgentPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.SensorInfoCardUrgentPreview() }
    @Test fun snapshot_SensorInfoCardEmptyPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.SensorInfoCardEmptyPreview() }
    @Test fun snapshot_ActiveTherapyCardPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.ActiveTherapyCardPreview() }
    @Test fun snapshot_ActiveTherapyCardExercisePreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.ActiveTherapyCardExercisePreview() }
    @Test fun snapshot_ActiveTherapyCardSleepPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.ActiveTherapyCardSleepPreview() }
    @Test fun snapshot_ActiveTherapyCardEmptyPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.ActiveTherapyCardEmptyPreview() }
    @Test fun snapshot_PumpStatusCardPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.PumpStatusCardPreview() }
    @Test fun snapshot_TherapyMetricsCardPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.TherapyMetricsCardPreview() }
    @Test fun snapshot_TherapyMetricsCardPartialPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.TherapyMetricsCardPartialPreview() }
    @Test fun snapshot_TherapyMetricsCardEmptyPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cards.TherapyMetricsCardEmptyPreview() }

    // Cartridge workflow screens
    @Test fun snapshot_CartridgeActionsMenuScreenPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.CartridgeActionsMenuScreenPreview() }
    @Test fun snapshot_CartridgeWorkflowScreenPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.CartridgeWorkflowScreenPreview() }
    @Test fun snapshot_ChangeCartridgeWorkflowScreenPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.ChangeCartridgeWorkflowScreenPreview() }
    @Test fun snapshot_FillTubingWorkflowScreenPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.FillTubingWorkflowScreenPreview() }
    @Test fun snapshot_FillCannulaWorkflowScreenPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.FillCannulaWorkflowScreenPreview() }

    // Dialogs
    @Ignore("Flaky under Robolectric: AppNotIdleException")
    @Test fun snapshot_AddProfileDialogPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.dialogs.AddProfileDialogPreview() }
    @Ignore("Flaky under Robolectric: AppNotIdleException")
    @Test fun snapshot_EditSegmentDialogPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.dialogs.EditSegmentDialogPreview() }
    @Ignore("Flaky under Robolectric: AppNotIdleException")
    @Test fun snapshot_AddSegmentDialogPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.dialogs.AddSegmentDialogPreview() }

    // Status components
    @Test fun snapshot_PumpStatusBarDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.PumpStatusBarDefaultPreview() }
    @Test fun snapshot_PumpStatusBarCharging() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.PumpStatusBarCharging() }
    @Test fun snapshot_VersionInfoDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.VersionInfoDefaultPreview() }
    @Test fun snapshot_DashboardCgmChartDefaultPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.DashboardCgmChartDefaultPreview() }

    // Pump critical-error UI — Layer A (bare PumpSetupStageDescription)
    @Test fun snapshot_PumpCriticalError_SetMtu_BelowThreshold_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_SetMtu_BelowThreshold_Preview() }
    @Test fun snapshot_PumpCriticalError_SetMtu_AboveThreshold_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_SetMtu_AboveThreshold_Preview() }
    @Test fun snapshot_PumpCriticalError_NotificationStateFailed_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_NotificationStateFailed_Preview() }
    @Test fun snapshot_PumpCriticalError_CharWriteFailed_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_CharWriteFailed_Preview() }
    @Test fun snapshot_PumpCriticalError_ConnectionUpdateFailed_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_ConnectionUpdateFailed_Preview() }
    @Test fun snapshot_PumpCriticalError_BtConn_AuthFailure_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_BtConn_AuthFailure_Preview() }
    @Test fun snapshot_PumpCriticalError_BtConn_Timeout_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_BtConn_Timeout_Preview() }
    @Test fun snapshot_PumpCriticalError_BtConn_Other_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_BtConn_Other_Preview() }
    @Test fun snapshot_PumpCriticalError_UnexpectedTxId_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_UnexpectedTxId_Preview() }
    @Test fun snapshot_PumpCriticalError_UnexpectedOpcode_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_UnexpectedOpcode_Preview() }
    @Test fun snapshot_PumpCriticalError_UnprocessableMessage_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_UnprocessableMessage_Preview() }
    @Test fun snapshot_PumpCriticalError_PairingCannotBegin_Tslim_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_PairingCannotBegin_Tslim_Preview() }
    @Test fun snapshot_PumpCriticalError_PairingCannotBegin_Mobi_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_PairingCannotBegin_Mobi_Preview() }
    @Test fun snapshot_PumpCriticalError_PairingPrompt_RetryAttempt0_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_PairingPrompt_RetryAttempt0_Preview() }
    @Test fun snapshot_PumpCriticalError_PairingPrompt_RetryAttempt2_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_PairingPrompt_RetryAttempt2_Preview() }
    @Test fun snapshot_PumpCriticalError_TconnectShare_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_TconnectShare_Preview() }
    @Test fun snapshot_PumpCriticalError_InvalidHmac_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_InvalidHmac_Preview() }
    @Test fun snapshot_PumpCriticalError_ErrorResponse_InvalidAuth_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_ErrorResponse_InvalidAuth_Preview() }
    @Test fun snapshot_PumpCriticalError_ErrorResponse_TxIdMismatch_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_ErrorResponse_TxIdMismatch_Preview() }
    @Test fun snapshot_PumpCriticalError_ErrorResponse_BadOpcode_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpCriticalError_ErrorResponse_BadOpcode_Preview() }

    // Pump critical-error UI — Layer B (PumpSetup initial-pairing surface)
    @Test fun snapshot_PumpSetup_PairingCannotBegin_Tslim_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpSetup_PairingCannotBegin_Tslim_Preview() }
    @Test fun snapshot_PumpSetup_PairingCannotBegin_Mobi_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpSetup_PairingCannotBegin_Mobi_Preview() }
    @Test fun snapshot_PumpSetup_PairingPrompt_RetryAttempt2_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpSetup_PairingPrompt_RetryAttempt2_Preview() }
    @Test fun snapshot_PumpSetup_InvalidHmac_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpSetup_InvalidHmac_Preview() }
    @Test fun snapshot_PumpSetup_TconnectShare_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpSetup_TconnectShare_Preview() }
    @Test fun snapshot_PumpSetup_BtConn_AuthFailure_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpSetup_BtConn_AuthFailure_Preview() }
    @Test fun snapshot_PumpSetup_SetMtu_AboveThreshold_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.PumpSetup_SetMtu_AboveThreshold_Preview() }

    // Pump critical-error UI — Layer C (Dashboard reconnect surface)
    @Test fun snapshot_Dashboard_Reconnect_NoError_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.Dashboard_Reconnect_NoError_Preview() }
    @Test fun snapshot_Dashboard_Reconnect_SetMtu_BelowThreshold_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.Dashboard_Reconnect_SetMtu_BelowThreshold_Preview() }
    @Test fun snapshot_Dashboard_Reconnect_SetMtu_AboveThreshold_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.Dashboard_Reconnect_SetMtu_AboveThreshold_Preview() }
    @Test fun snapshot_Dashboard_Reconnect_BtConn_Timeout_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.Dashboard_Reconnect_BtConn_Timeout_Preview() }
    @Test fun snapshot_Dashboard_Reconnect_BtConn_AuthFailure_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.Dashboard_Reconnect_BtConn_AuthFailure_Preview() }
    @Test fun snapshot_Dashboard_Reconnect_TconnectShare_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.Dashboard_Reconnect_TconnectShare_Preview() }
    @Test fun snapshot_Dashboard_Reconnect_UnexpectedTxId_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.Dashboard_Reconnect_UnexpectedTxId_Preview() }
    @Test fun snapshot_Dashboard_Reconnect_ErrorResponse_BadOpcode_Preview() = snapshot { com.jwoglom.controlx2.presentation.preview.Dashboard_Reconnect_ErrorResponse_BadOpcode_Preview() }

    // Vico CGM chart variants
    @Test fun snapshot_VicoCgmChartCardNormalPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.VicoCgmChartCardNormalPreview() }
    @Test fun snapshot_VicoCgmChartCardHighPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.VicoCgmChartCardHighPreview() }
    @Test fun snapshot_VicoCgmChartCardLowPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.VicoCgmChartCardLowPreview() }
    @Test fun snapshot_VicoCgmChartCardSteadyPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.VicoCgmChartCardSteadyPreview() }
    @Test fun snapshot_VicoCgmChartCardVolatilePreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.VicoCgmChartCardVolatilePreview() }
    @Test fun snapshot_VicoCgmChartCardWithBolusPreview() = snapshot { com.jwoglom.controlx2.presentation.screens.sections.components.VicoCgmChartCardWithBolusPreview() }

    private fun snapshot(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                content()
            }
        }
        val methodName = Throwable().stackTrace.first { it.methodName.startsWith("snapshot_") }.methodName
        val outputPath = "src/test/snapshots/roborazzi/ComposePreviewSnapshotTests/$methodName.png"
        File(outputPath).parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(filePath = outputPath)
    }
}
