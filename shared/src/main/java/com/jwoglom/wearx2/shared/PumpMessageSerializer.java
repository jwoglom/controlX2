package com.jwoglom.wearx2.shared;

import com.jwoglom.pumpx2.pump.messages.Message;
import com.jwoglom.pumpx2.pump.messages.Messages;
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic;
import com.jwoglom.pumpx2.shared.Hex;

import org.apache.commons.codec.DecoderException;
import org.json.JSONException;
import org.json.JSONObject;

import timber.log.Timber;

public class PumpMessageSerializer {
    public static byte[] toBytes(Message msg) {
        JSONObject json = new JSONObject();
        try {
            json.put("cargo", Hex.encodeHexString(msg.getCargo()));
            json.put("opCode", (int) msg.opCode());
            json.put("characteristic", msg.getCharacteristic().name());
        } catch (JSONException e) {
            Timber.e(e);
            return null;
        }
        return json.toString().getBytes();
    }

    public static Message fromBytes(byte[] bytes) {
        JSONObject json = null;
        try {
            json = new JSONObject(new String(bytes));
            int opCode = json.getInt("opCode");
            Characteristic characteristic = Characteristic.valueOf(json.getString("characteristic"));
            byte[] cargo = Hex.decodeHex(json.getString("cargo"));
            return Messages.parse(cargo, opCode, characteristic);
        } catch (JSONException | DecoderException e) {
            Timber.e(e);
            return null;
        }
    }
}
