package com.jwoglom.controlx2.pump

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

enum class BondState(val id: Int) {
    NOT_BONDED(10),
    BONDING(11),
    BONDED(12),
    ;
    companion object {
        private val map = BondState.values().associateBy(BondState::id)
        fun fromId(type: Int) = map[type]
    }
}

class BleChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            "android.bluetooth.device.action.BOND_STATE_CHANGED" -> {
                val bondState = BondState.fromId(intent.getIntExtra(
                    "android.bluetooth.device.extra.BOND_STATE",
                    Int.MIN_VALUE
                ))
                Timber.i("BleChangeReceiver BOND_STATE_CHANGED: $bondState")
            }
            "android.bluetooth.adapter.action.STATE_CHANGED" -> {
                when (intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Int.MIN_VALUE)) {
                    10, 13 -> {
                        Timber.i("BleChangeReceiver STATE_CHANGED: off")
                    }
                    12 -> {
                        Timber.i("BleChangeReceiver STATE_CHANGED: on")
                    }
                }
            }
        }
    }
}
