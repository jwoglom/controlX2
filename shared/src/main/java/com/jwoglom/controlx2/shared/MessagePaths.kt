package com.jwoglom.controlx2.shared

// Constants for all message paths used in phone-watch-pump communication.
//
// Naming convention (role-based, not device-specific):
// - /to-server/  -- commands sent TO the pump-host device (whichever device manages the BT pump connection)
// - /to-client/  -- data/events sent TO the client device (the device not directly connected to the pump)
// - /to-pump/    -- commands sent TO the pump (unchanged, always refers to the physical pump)
// - /from-pump/  -- events FROM the pump (unchanged, always refers to the physical pump)
object MessagePaths {
    // Prefix constants (used in startsWith checks)
    const val PREFIX_TO_SERVER = "/to-server/"
    const val PREFIX_TO_CLIENT = "/to-client/"
    const val PREFIX_TO_PUMP = "/to-pump/"
    const val PREFIX_FROM_PUMP = "/from-pump/"

    // === /to-server/* — commands sent TO the pump-host device ===
    const val TO_SERVER_APP_RELOAD = "/to-server/app-reload"
    const val TO_SERVER_BOLUS_CANCEL = "/to-server/bolus-cancel"
    const val TO_SERVER_BOLUS_CONFIRM_DIALOG = "/to-server/bolus-confirm-dialog"
    const val TO_SERVER_BOLUS_REQUEST_PHONE = "/to-server/bolus-request-phone"
    const val TO_SERVER_BOLUS_REQUEST_WEAR = "/to-server/bolus-request-wear"
    const val TO_SERVER_CHECK_PUMP_FINDER_FOUND_PUMPS = "/to-server/check-pump-finder-found-pumps"
    const val TO_SERVER_COMM_STARTED = "/to-server/comm-started"
    const val TO_SERVER_CONNECTED = "/to-server/connected"
    const val TO_SERVER_FORCE_RELOAD = "/to-server/force-reload"
    const val TO_SERVER_INITIATE_CONFIRMED_BOLUS = "/to-server/initiate-confirmed-bolus"
    const val TO_SERVER_IS_PUMP_CONNECTED = "/to-server/is-pump-connected"
    const val TO_SERVER_PUMP_FINDER_STARTED = "/to-server/pump-finder-started"
    const val TO_SERVER_REFRESH_HISTORY_LOG_SYNC = "/to-server/refresh-history-log-sync"
    const val TO_SERVER_REQUEST_SERVICE_STATUS = "/to-server/request-service-status"
    const val TO_SERVER_RESTART_PUMP_FINDER = "/to-server/restart-pump-finder"
    const val TO_SERVER_SERVICE_STATUS_ACKNOWLEDGED = "/to-server/service-status-acknowledged"
    const val TO_SERVER_SET_PAIRING_CODE = "/to-server/set-pairing-code"
    const val TO_SERVER_START_COMM = "/to-server/start-comm"
    const val TO_SERVER_START_PUMP_FINDER = "/to-server/start-pump-finder"
    const val TO_SERVER_STOP_COMM = "/to-server/stop-comm"
    const val TO_SERVER_STOP_PUMP_FINDER = "/to-server/stop-pump-finder"
    const val TO_SERVER_WRITE_CHARACTERISTIC_FAILED_CALLBACK = "/to-server/write-characteristic-failed-callback"

    // === /to-client/* — data/events sent TO the client device ===
    const val TO_CLIENT_BLOCKED_BOLUS_SIGNATURE = "/to-client/blocked-bolus-signature"
    // NOTE: PumpCommHandler uses this variant - consider unifying with TO_CLIENT_BLOCKED_BOLUS_SIGNATURE
    const val TO_CLIENT_BOLUS_BLOCKED_SIGNATURE = "/to-client/bolus-blocked-signature"
    const val TO_CLIENT_BOLUS_MIN_NOTIFY_THRESHOLD = "/to-client/bolus-min-notify-threshold"
    const val TO_CLIENT_BOLUS_NOT_ENABLED = "/to-client/bolus-not-enabled"
    const val TO_CLIENT_BOLUS_REJECTED = "/to-client/bolus-rejected"
    const val TO_CLIENT_CONNECTED = "/to-client/connected"
    const val TO_CLIENT_GLUCOSE_UNIT = "/to-client/glucose-unit"
    const val TO_CLIENT_INITIATE_CONFIRMED_BOLUS = "/to-client/initiate-confirmed-bolus"
    const val TO_CLIENT_OPEN_ACTIVITY = "/to-client/open-activity"
    const val TO_CLIENT_SERVICE_RECEIVE_MESSAGE = "/to-client/service-receive-message"
    const val TO_CLIENT_WEAR_AUTO_APPROVE_TIMEOUT = "/to-client/wear-auto-approve-timeout"

    // === /to-pump/* — commands sent TO the pump ===
    const val TO_PUMP_CACHED_COMMANDS = "/to-pump/cached-commands"
    const val TO_PUMP_COMMAND = "/to-pump/command"
    const val TO_PUMP_COMMANDS = "/to-pump/commands"
    const val TO_PUMP_COMMANDS_BUST_CACHE = "/to-pump/commands-bust-cache"
    const val TO_PUMP_DEBUG_COMMANDS = "/to-pump/debug-commands"
    const val TO_PUMP_DEBUG_HISTORYLOG_CACHE = "/to-pump/debug-historylog-cache"
    const val TO_PUMP_DEBUG_MESSAGE_CACHE = "/to-pump/debug-message-cache"
    const val TO_PUMP_DEBUG_WRITE_BT_CHARACTERISTIC = "/to-pump/debug-write-bt-characteristic"
    const val TO_PUMP_PAIR = "/to-pump/pair"

    // === /from-pump/* — events FROM the pump ===
    const val FROM_PUMP_DEBUG_HISTORYLOG_CACHE = "/from-pump/debug-historylog-cache"
    const val FROM_PUMP_DEBUG_MESSAGE_CACHE = "/from-pump/debug-message-cache"
    const val FROM_PUMP_ENTERED_PAIRING_CODE = "/from-pump/entered-pairing-code"
    const val FROM_PUMP_INITIAL_PUMP_CONNECTION = "/from-pump/initial-pump-connection"
    const val FROM_PUMP_INVALID_PAIRING_CODE = "/from-pump/invalid-pairing-code"
    const val FROM_PUMP_MISSING_PAIRING_CODE = "/from-pump/missing-pairing-code"
    const val FROM_PUMP_PUMP_BONDED_NEEDS_MANUAL_UNBOND = "/from-pump/pump-bonded-needs-manual-unbond"
    const val FROM_PUMP_PUMP_CONNECTED = "/from-pump/pump-connected"
    const val FROM_PUMP_PUMP_CRITICAL_ERROR = "/from-pump/pump-critical-error"
    const val FROM_PUMP_PUMP_DISCONNECTED = "/from-pump/pump-disconnected"
    const val FROM_PUMP_PUMP_DISCOVERED = "/from-pump/pump-discovered"
    const val FROM_PUMP_PUMP_FINDER_BLUETOOTH_STATE = "/from-pump/pump-finder-bluetooth-state"
    const val FROM_PUMP_PUMP_FINDER_FOUND_PUMPS = "/from-pump/pump-finder-found-pumps"
    const val FROM_PUMP_PUMP_FINDER_PUMP_DISCOVERED = "/from-pump/pump-finder-pump-discovered"
    const val FROM_PUMP_PUMP_MODEL = "/from-pump/pump-model"
    const val FROM_PUMP_PUMP_NOT_CONNECTED = "/from-pump/pump-not-connected"
    const val FROM_PUMP_RECEIVE_CACHED_MESSAGE = "/from-pump/receive-cached-message"
    const val FROM_PUMP_RECEIVE_MESSAGE = "/from-pump/receive-message"
    const val FROM_PUMP_RECEIVE_QUALIFYING_EVENT = "/from-pump/receive-qualifying-event"
}
