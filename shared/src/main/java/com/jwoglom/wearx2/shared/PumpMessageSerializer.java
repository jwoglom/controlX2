package com.jwoglom.wearx2.shared;

import com.jwoglom.pumpx2.pump.messages.Message;
import com.jwoglom.pumpx2.pump.messages.Messages;
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic;
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes;
import com.jwoglom.pumpx2.shared.Hex;

import org.apache.commons.codec.DecoderException;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

    public static byte[] toBulkBytes(List<Message> msgsList) {
        byte[] total = new byte[0];
        Message[] msgs = msgsList.toArray(new Message[0]);
        for (int i=0; i<msgs.length; i++) {
            total = Bytes.combine(total, toBytes(msgs[i]));
            if (i != msgs.length-1) {
                total = Bytes.combine(total, ";;;".getBytes());
            }
        }
        return total;
    }

    public static List<Message> fromBulkBytes(byte[] bytes) {
        List<Message> messages = new ArrayList<>();
        String tmp = new String(bytes);
        for (String m : tmp.split(";;;")) {
            messages.add(fromBytes(m.getBytes()));
        }
        return messages;
    }
}
