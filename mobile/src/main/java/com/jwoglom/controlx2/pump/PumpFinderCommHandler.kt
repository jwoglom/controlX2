package com.jwoglom.controlx2.pump

import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.bluetooth.TandemPumpFinder
import com.welie.blessed.BluetoothPeripheral
import timber.log.Timber

/**
 * Handler for pump discovery mode. Manages scanning for available Tandem pumps
 * and reporting discovered pumps back via callbacks.
 *
 * Extracted from CommService's inner class to enable independent testing
 * and future module extraction.
 */
class PumpFinderCommHandler(
    looper: Looper,
    private val callbacks: CommServiceCallbacks
) : Handler(looper) {

    private lateinit var pumpFinder: PumpFinder
    private var pumpFinderActive = false
    private var foundPumps = mutableListOf<String>()

    private inner class PumpFinder : TandemPumpFinder(callbacks.getApplicationContext(), null) {
        init {
            Timber.i("PumpFinder init")
        }

        override fun toString(): String {
            return "PumpFinder(pumpFinderActive=${pumpFinderActive},foundPumps=${foundPumps.joinToString(";")})"
        }

        override fun onDiscoveredPump(
            peripheral: BluetoothPeripheral?,
            scanResult: ScanResult?,
            readyState: PumpReadyState
        ) {
            val name = when {
                peripheral?.name.isNullOrEmpty() -> "NO NAME"
                else -> peripheral?.name
            }
            val key = "${name}=${peripheral?.address}"
            callbacks.sendWearCommMessage(
                MessagePaths.FROM_PUMP_PUMP_FINDER_PUMP_DISCOVERED,
                "${key};;${readyState.name}".toByteArray()
            )
            // Keep emitting ready-state transitions, but only add each pump once to selection list.
            if (!foundPumps.contains(key)) {
                foundPumps.add(key)
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_FINDER_FOUND_PUMPS,
                    foundPumps.joinToString(";").toByteArray()
                )
            }
        }

        override fun onBluetoothState(bluetoothEnabled: Boolean) {
            callbacks.sendWearCommMessage(
                MessagePaths.FROM_PUMP_PUMP_FINDER_BLUETOOTH_STATE,
                "${bluetoothEnabled}".toByteArray()
            )
        }
    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            CommServiceCodes.INIT_PUMP_FINDER_COMM.ordinal -> {
                Timber.i("pumpFinderCommHandler: init_pump_comm")
                try {
                    pumpFinder = PumpFinder()
                } catch (e: SecurityException) {
                    Timber.e("pumpFinderCommHandler: SecurityException starting pump $e")
                }
                while (true) {
                    try {
                        Timber.i("pumpFinderCommHandler: Starting scan...")
                        pumpFinder.startScan()
                        pumpFinderActive = true
                        break
                    } catch (e: SecurityException) {
                        Timber.e("pumpFinderCommHandler: Waiting for BT permissions $e")
                        Thread.sleep(500)
                    }
                }
            }
            CommServiceCodes.STOP_PUMP_FINDER_COMM.ordinal -> {
                pumpFinder.stop()
                pumpFinderActive = false
                foundPumps.clear()
            }
            CommServiceCodes.CHECK_PUMP_FINDER_FOUND_PUMPS.ordinal -> {
                if (pumpFinderActive) {
                    callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_FINDER_FOUND_PUMPS,
                        foundPumps.joinToString(";").toByteArray()
                    )
                }
            }
        }
    }
}
