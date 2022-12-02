package com.jwoglom.wearx2;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.jwoglom.pumpx2.pump.PumpState;
import com.jwoglom.pumpx2.pump.TandemError;
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump;
import com.jwoglom.pumpx2.pump.messages.Message;
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusPermissionChangeReasonRequest;
import com.jwoglom.pumpx2.pump.messages.response.authentication.CentralChallengeResponse;
import com.jwoglom.pumpx2.pump.messages.response.authentication.PumpChallengeResponse;
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse;
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse;
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusPermissionChangeReasonResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMHardwareInfoResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusV2Response;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.NonControlIQIOBResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpFeaturesV1Response;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse;
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse;
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog;
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse;
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent;
import com.jwoglom.pumpx2.shared.Hex;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.HciStatus;

import java.util.Set;

import timber.log.Timber;

public class WearX2TandemPump extends TandemPump {
    public WearX2TandemPump(Context context) {
        super(context);
    }

    @Override
    public void onInitialPumpConnection(BluetoothPeripheral peripheral) {
        super.onInitialPumpConnection(peripheral);

//        Intent intent = new Intent(PUMP_CONNECTED_STAGE1_INTENT);
//        intent.putExtra("address", peripheral.getAddress());
//        intent.putExtra("name", peripheral.getName());
//        context.sendBroadcast(intent);
    }

    @Override
    public void onInvalidPairingCode(BluetoothPeripheral peripheral, PumpChallengeResponse resp) {
//        Intent intent = new Intent(PUMP_INVALID_CHALLENGE_INTENT);
//        intent.putExtra("address", peripheral.getAddress());
//        intent.putExtra("appInstanceId", resp.getAppInstanceId());
//        context.sendBroadcast(intent);
    }

    @Override
    public void onReceiveMessage(BluetoothPeripheral peripheral, Message message) {

    }

    @Override
    public void onReceiveQualifyingEvent(BluetoothPeripheral peripheral, Set<QualifyingEvent> events) {
        Timber.i("Received QualifyingEvents: %s", events);
        Toast.makeText(context, "Received QualifyingEvents: " + events, Toast.LENGTH_LONG).show();
//        if (events.contains(QualifyingEvent.BOLUS_PERMISSION_REVOKED) && bolusInProgress) {
//            sendCommand(peripheral, new BolusPermissionChangeReasonRequest(lastBolusId));
//        }
    }

    @Override
    public void onWaitingForPairingCode(BluetoothPeripheral peripheral, CentralChallengeResponse centralChallenge) {
        // checkHmac(authKey, centralChallenge we sent, new byte[0])
        // doHmacSha1(10 bytes from central challenge request, bytes from authKey/pairing code) == 2-22 of response
//        Intent intent = new Intent(PUMP_CONNECTED_STAGE2_INTENT);
//        intent.putExtra("address", peripheral.getAddress());
//        intent.putExtra("centralChallengeCargo", Hex.encodeHexString(centralChallenge.getCargo()));
//        context.sendBroadcast(intent);
    }

    @Override
    public void onPumpConnected(BluetoothPeripheral peripheral) {
        super.onPumpConnected(peripheral);
//
//        Intent intent = new Intent(PUMP_CONNECTED_STAGE3_INTENT);
//        intent.putExtra("address", peripheral.getAddress());
//        context.sendBroadcast(intent);
    }


    @Override
    public boolean onPumpDisconnected(BluetoothPeripheral peripheral, HciStatus status) {
        Toast.makeText(context, "Pump disconnected: " + status, Toast.LENGTH_SHORT).show();
        return super.onPumpDisconnected(peripheral, status);
    }

    @Override
    public void onPumpCriticalError(BluetoothPeripheral peripheral, TandemError reason) {
//        Intent intent = new Intent(PUMP_ERROR_INTENT);
//        intent.putExtra("reason", reason.name());
//        intent.putExtra("message", reason.getMessage());
//        intent.putExtra("extra", reason.getExtra());
//        context.sendBroadcast(intent);
    }
}
