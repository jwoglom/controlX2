package com.jwoglom.controlx2.shared.enums

/**
 * Stages of the coordinated switch-pump-host wizard. Both devices persist
 * their current stage so the wizard survives Activity recreate / process death.
 *
 * Linear progression with two terminal states (Done, Failed/Cancelled):
 *
 *   Idle
 *    -> WaitingForPeerAck       (initiator sent TO_*_WIZARD_START)
 *    -> ReleasingBond           (current pump-host)
 *    -> WaitingPeerBondCleared  (other side, mirroring)
 *    -> FlippingRoles
 *    -> WaitingPeerRoleFlipped
 *    -> Repairing               (new host runs PumpFinder; old host mirrors)
 *    -> Done
 *    -> Failed | Cancelled
 */
enum class SwitchHostWizardStage {
    Idle,
    WaitingForPeerAck,
    ReleasingBond,
    WaitingPeerBondCleared,
    FlippingRoles,
    WaitingPeerRoleFlipped,
    Repairing,
    Done,
    Failed,
    Cancelled,
    ;

    fun isTerminal(): Boolean = this == Done || this == Failed || this == Cancelled
}
