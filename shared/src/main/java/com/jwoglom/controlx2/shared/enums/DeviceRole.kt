package com.jwoglom.controlx2.shared.enums

/**
 * Defines the role of this device in the pump communication architecture.
 * Selected at setup time; switching requires re-pairing.
 */
enum class DeviceRole {
    /** This device manages the Bluetooth connection to the Tandem pump. */
    PUMP_HOST,

    /** This device is a thin client of the pump-host device. */
    CLIENT
}
