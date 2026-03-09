package com.jwoglom.controlx2.shared;

import com.jwoglom.pumpx2.pump.messages.Message;
import com.jwoglom.pumpx2.pump.messages.Messages;
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic;
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes;
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog;
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser;
import com.jwoglom.pumpx2.shared.Hex;

import org.apache.commons.codec.DecoderException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import kotlin.Pair;
import timber.log.Timber;

public class PumpMessageSerializer {
    private static final String MESSAGE_PACKAGE_PREFIX = "com.jwoglom.pumpx2.pump.messages.";
    private static volatile Map<String, List<MessageLookup>> MESSAGE_NAME_TO_LOOKUP = null;

    private static final class MessageLookup {
        private final int opCode;
        private final Characteristic characteristic;

        private MessageLookup(int opCode, Characteristic characteristic) {
            this.opCode = opCode;
            this.characteristic = characteristic;
        }
    }

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

            Characteristic characteristic = null;
            if (json.has("characteristic") && !json.isNull("characteristic")) {
                characteristic = Characteristic.valueOf(json.getString("characteristic"));
            }

            Integer opCode = json.has("opCode") ? json.getInt("opCode") : null;
            if (json.has("name")) {
                Pair<Integer, Characteristic> inferred = inferOpCodeFromMessageName(
                        json.getString("name"),
                        characteristic
                );
                if (inferred != null) {
                    if (opCode == null) {
                        opCode = inferred.getFirst();
                    }
                    if (characteristic == null) {
                        characteristic = inferred.getSecond();
                    }
                }
            }

            if (characteristic == null && opCode != null) {
                characteristic = inferCharacteristicFromOpCode(opCode);
            }

            if (opCode == null || characteristic == null) {
                throw new JSONException("Missing opCode/characteristic and unable to infer from name");
            }

            byte[] cargo = Hex.decodeHex(json.getString("cargo"));
            return Messages.parse(cargo, opCode, characteristic);
        } catch (JSONException | DecoderException e) {
            Timber.e(e);
            return null;
        }
    }

    private static Pair<Integer, Characteristic> inferOpCodeFromMessageName(String name, Characteristic providedCharacteristic) {
        if (name == null) {
            return null;
        }
        String key = normalizeMessageName(name);
        List<MessageLookup> candidates = getMessageNameToLookup().get(key);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<MessageLookup> filtered = new ArrayList<>();
        for (MessageLookup candidate : candidates) {
            if (providedCharacteristic == null || candidate.characteristic == providedCharacteristic) {
                addLookupIfAbsent(filtered, candidate);
            }
        }

        if (filtered.size() == 1) {
            MessageLookup result = filtered.get(0);
            return new Pair<>(result.opCode, result.characteristic);
        }

        return null;
    }

    private static Characteristic inferCharacteristicFromOpCode(int opCode) {
        java.util.Set<Characteristic> characteristics = Messages.findPossibleCharacteristicsForOpcode(opCode);
        if (characteristics.size() == 1) {
            return characteristics.iterator().next();
        }
        return null;
    }

    private static Map<String, List<MessageLookup>> getMessageNameToLookup() {
        if (MESSAGE_NAME_TO_LOOKUP == null) {
            synchronized (PumpMessageSerializer.class) {
                if (MESSAGE_NAME_TO_LOOKUP == null) {
                    Map<String, List<MessageLookup>> map = new HashMap<>();
                    for (Messages m : Messages.values()) {
                        addMessageNameMapping(map, m.name(), m.requestOpCode(), m.requestProps().characteristic());
                        addMessageClassMappings(map, m.requestClass(), m.requestOpCode(), m.requestProps().characteristic());
                        addMessageClassMappings(map, m.responseClass(), m.responseOpCode(), m.responseProps().characteristic());
                    }
                    MESSAGE_NAME_TO_LOOKUP = map;
                }
            }
        }
        return MESSAGE_NAME_TO_LOOKUP;
    }

    private static void addMessageClassMappings(Map<String, List<MessageLookup>> map, Class<? extends Message> cls, int opCode, Characteristic characteristic) {
        if (cls == null) {
            return;
        }
        addMessageNameMapping(map, cls.getSimpleName(), opCode, characteristic);

        String canonicalName = cls.getName();
        addMessageNameMapping(map, canonicalName, opCode, characteristic);
        if (canonicalName.startsWith(MESSAGE_PACKAGE_PREFIX)) {
            addMessageNameMapping(map, canonicalName.substring(MESSAGE_PACKAGE_PREFIX.length()), opCode, characteristic);
        }
    }

    private static void addMessageNameMapping(Map<String, List<MessageLookup>> map, String name, int opCode, Characteristic characteristic) {
        if (name == null) {
            return;
        }
        String key = normalizeMessageName(name);
        List<MessageLookup> lookups = map.computeIfAbsent(key, k -> new ArrayList<>());
        addLookupIfAbsent(lookups, new MessageLookup(opCode, characteristic));
    }

    private static void addLookupIfAbsent(List<MessageLookup> lookups, MessageLookup candidate) {
        for (MessageLookup current : lookups) {
            if (current.opCode == candidate.opCode && current.characteristic == candidate.characteristic) {
                return;
            }
        }
        lookups.add(candidate);
    }

    private static String normalizeMessageName(String name) {
        return name.trim().toLowerCase(Locale.US);
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
