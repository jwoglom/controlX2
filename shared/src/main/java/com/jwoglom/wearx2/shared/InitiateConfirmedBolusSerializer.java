package com.jwoglom.wearx2.shared;

import com.jwoglom.pumpx2.pump.messages.Message;
import com.jwoglom.pumpx2.pump.messages.Messages;
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic;
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes;
import com.jwoglom.pumpx2.shared.Hex;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class InitiateConfirmedBolusSerializer {
    public static byte[] toBytes(String secret, Message message) {
        JSONObject json = new JSONObject();
        try {
            byte[] messageBytes = PumpMessageSerializer.toBytes(message);
            if (messageBytes == null) {
                return null;
            }
            json.put("hash", Hex.encodeHexString(new HmacUtils(HmacAlgorithms.HMAC_SHA_1, secret).hmac(messageBytes)));
            json.put("messageBytesHex", Hex.encodeHexString(messageBytes));
        } catch (JSONException e) {
            Timber.e(e);
            return null;
        }
        return json.toString().getBytes();
    }

    public static Pair<Boolean, Message> fromBytes(String correctSecret, byte[] bytes) {
        JSONObject json = null;
        try {
            json = new JSONObject(new String(bytes));
            String messageHash = json.getString("hash");
            byte[] messageBytes = Hex.decodeHex(json.getString("messageBytesHex"));
            String expectedHash = Hex.encodeHexString(new HmacUtils(HmacAlgorithms.HMAC_SHA_1, correctSecret).hmac(messageBytes));
            if (expectedHash.equals(messageHash)) {
                return Pair.of(true, PumpMessageSerializer.fromBytes(messageBytes));
            }
            Timber.i("computed messageHash does not match: " + messageHash + " expected: " + expectedHash);
            return Pair.of(false, PumpMessageSerializer.fromBytes(messageBytes));
        } catch (JSONException | DecoderException e) {
            Timber.e(e);
            return null;
        }
    }
}
