package com.jwoglom.controlx2.clientcomm

/**
 * Role-based connection state for a client device communicating with a pump-host.
 * "Host" is whichever device holds the BLE connection to the pump (phone or watch).
 */
enum class ClientConnectionState {
    UNKNOWN,
    HOST_DISCONNECTED,
    HOST_CONNECTED_PUMP_DISCONNECTED,
    HOST_CONNECTED_PUMP_CONNECTED,
}
