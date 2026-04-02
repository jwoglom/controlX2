package com.jwoglom.controlx2.shared

/**
 * Constants for all message paths used in phone-watch-pump communication.
 * Centralizes string literals to prevent typos and enable future renames.
 */
object MessagePaths {
    // Prefix constants (used in startsWith checks)
    const val PREFIX_TO_PHONE = "/to-phone/"
    const val PREFIX_TO_WEAR = "/to-wear/"
    const val PREFIX_TO_PUMP = "/to-pump/"
    const val PREFIX_FROM_PUMP = "/from-pump/"

    // === /to-phone/* — commands sent TO the phone (pump-host device) ===
    const val TO_PHONE_APP_RELOAD = "/to-phone/app-reload"
    const val TO_PHONE_BOLUS_CANCEL = "/to-phone/bolus-cancel"
    const val TO_PHONE_BOLUS_CONFIRM_DIALOG = "/to-phone/bolus-confirm-dialog"
    const val TO_PHONE_BOLUS_REQUEST_PHONE = "/to-phone/bolus-request-phone"
    const val TO_PHONE_BOLUS_REQUEST_WEAR = "/to-phone/bolus-request-wear"
    const val TO_PHONE_CHECK_PUMP_FINDER_FOUND_PUMPS = "/to-phone/check-pump-finder-found-pumps"
    const val TO_PHONE_COMM_STARTED = "/to-phone/comm-started"
    const val TO_PHONE_CONNECTED = "/to-phone/connected"
    const val TO_PHONE_FORCE_RELOAD = "/to-phone/force-reload"
    const val TO_PHONE_INITIATE_CONFIRMED_BOLUS = "/to-phone/initiate-confirmed-bolus"
    const val TO_PHONE_IS_PUMP_CONNECTED = "/to-phone/is-pump-connected"
    const val TO_PHONE_PUMP_FINDER_STARTED = "/to-phone/pump-finder-started"
    const val TO_PHONE_REFRESH_HISTORY_LOG_SYNC = "/to-phone/refresh-history-log-sync"
    const val TO_PHONE_REQUEST_SERVICE_STATUS = "/to-phone/request-service-status"
    const val TO_PHONE_RESTART_PUMP_FINDER = "/to-phone/restart-pump-finder"
    const val TO_PHONE_SERVICE_STATUS_ACKNOWLEDGED = "/to-phone/service-status-acknowledged"
    const val TO_PHONE_SET_PAIRING_CODE = "/to-phone/set-pairing-code"
    const val TO_PHONE_START_COMM = "/to-phone/start-comm"
    const val TO_PHONE_START_PUMP_FINDER = "/to-phone/start-pump-finder"
    const val TO_PHONE_STOP_COMM = "/to-phone/stop-comm"
    const val TO_PHONE_STOP_PUMP_FINDER = "/to-phone/stop-pump-finder"
    const val TO_PHONE_WRITE_CHARACTERISTIC_FAILED_CALLBACK = "/to-phone/write-characteristic-failed-callback"

    // === /to-wear/* — data/events sent TO the client device ===
    const val TO_WEAR_BLOCKED_BOLUS_SIGNATURE = "/to-wear/blocked-bolus-signature"
    // NOTE: PumpCommHandler uses this variant — consider unifying with TO_WEAR_BLOCKED_BOLUS_SIGNATURE
    const val TO_WEAR_BOLUS_BLOCKED_SIGNATURE = "/to-wear/bolus-blocked-signature"
    const val TO_WEAR_BOLUS_MIN_NOTIFY_THRESHOLD = "/to-wear/bolus-min-notify-threshold"
    const val TO_WEAR_BOLUS_NOT_ENABLED = "/to-wear/bolus-not-enabled"
    const val TO_WEAR_BOLUS_REJECTED = "/to-wear/bolus-rejected"
    const val TO_WEAR_CONNECTED = "/to-wear/connected"
    const val TO_WEAR_GLUCOSE_UNIT = "/to-wear/glucose-unit"
    const val TO_WEAR_INITIATE_CONFIRMED_BOLUS = "/to-wear/initiate-confirmed-bolus"
    const val TO_WEAR_OPEN_ACTIVITY = "/to-wear/open-activity"
    const val TO_WEAR_SERVICE_RECEIVE_MESSAGE = "/to-wear/service-receive-message"
    const val TO_WEAR_WEAR_AUTO_APPROVE_TIMEOUT = "/to-wear/wear-auto-approve-timeout"

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
