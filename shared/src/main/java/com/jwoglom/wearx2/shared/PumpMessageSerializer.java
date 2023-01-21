package com.jwoglom.wearx2.shared;

import com.google.common.collect.ImmutableList;
import com.jwoglom.pumpx2.pump.messages.Message;
import com.jwoglom.pumpx2.pump.messages.Messages;
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic;
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes;
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog;
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser;
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse;
import com.jwoglom.pumpx2.shared.Hex;

import org.apache.commons.codec.DecoderException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kotlin.Pair;
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

    public static byte[] historyLogToBytes(HistoryLog msg) {
        JSONObject json = new JSONObject();
        try {
            json.put("cargo", Hex.encodeHexString(msg.getCargo()));
        } catch (JSONException e) {
            Timber.e(e);
            return null;
        }
        return json.toString().getBytes();
    }

    public static HistoryLog historyLogFromBytes(byte[] bytes) {
        JSONObject json = null;
        try {
            json = new JSONObject(new String(bytes));
            byte[] cargo = Hex.decodeHex(json.getString("cargo"));
            return HistoryLogParser.parse(cargo);
        } catch (JSONException | DecoderException e) {
            Timber.e(e);
            return null;
        }
    }

    public static byte[] toDebugMessageCacheBytes(Collection<Pair<Message, Instant>> messages) {
        try {
            JSONArray array = new JSONArray();
            for (Pair<Message, Instant> pair : messages) {
                JSONObject obj = new JSONObject();
                obj.put("message", Hex.encodeHexString(Objects.requireNonNull(toBytes(pair.getFirst()))));
                obj.put("instant", pair.getSecond().toEpochMilli());
                array.put(obj);
            }
            return array.toString().getBytes();
        } catch (JSONException e) {
            Timber.e(e);
            return null;
        }
    }

    public static List<Pair<Message, Instant>> fromDebugMessageCacheBytes(byte[] bytes) {
        List<Pair<Message, Instant>> ret = new ArrayList<>();
        JSONArray array = null;
        try {
            array = new JSONArray(new String(bytes));
            for (int i=0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Message msg = fromBytes(Hex.decodeHex(obj.getString("message")));
                Instant instant = Instant.ofEpochMilli(obj.getLong("instant"));
                ret.add(new Pair<>(msg, instant));
            }
            return ret;
        } catch (JSONException | DecoderException e) {
            Timber.e(e);
            return null;
        }
    }

    public static byte[] toDebugHistoryLogCacheBytes(Map<Long, HistoryLog> messages) {
        try {
            JSONArray array = new JSONArray();
            for (Map.Entry<Long, HistoryLog> pair : messages.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put("seq", pair.getKey());
                obj.put("message", Hex.encodeHexString(Objects.requireNonNull(historyLogToBytes(pair.getValue()))));
                array.put(obj);
            }
            return array.toString().getBytes();
        } catch (JSONException e) {
            Timber.e(e);
            return null;
        }
    }

    public static Map<Long, HistoryLog> fromDebugHistoryLogCacheBytes(byte[] bytes) {
        Map<Long, HistoryLog> ret = new HashMap<>();
        JSONArray array = null;
        try {
            array = new JSONArray(new String(bytes));
            for (int i=0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                HistoryLog msg = historyLogFromBytes(Hex.decodeHex(obj.getString("message")));
                Long seq = obj.getLong("seq");
                ret.put(seq, msg);
            }
            return ret;
        } catch (JSONException | DecoderException e) {
            Timber.e(e);
            return null;
        }
    }
}
