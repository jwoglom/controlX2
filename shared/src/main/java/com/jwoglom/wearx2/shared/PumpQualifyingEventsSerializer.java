package com.jwoglom.wearx2.shared;

import com.jwoglom.pumpx2.pump.messages.Message;
import com.jwoglom.pumpx2.pump.messages.Messages;
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic;
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent;
import com.jwoglom.pumpx2.shared.Hex;

import org.apache.commons.codec.DecoderException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import timber.log.Timber;

public class PumpQualifyingEventsSerializer {
    public static byte[] toBytes(Set<QualifyingEvent> events) {
        JSONObject json = new JSONObject();
        JSONArray array = new JSONArray();
        try {
            for (QualifyingEvent event : events) {
                array.put(event.name());
            }
            json.put("events", array);
        } catch (JSONException e) {
            Timber.e(e);
            return null;
        }
        return json.toString().getBytes();
    }

    public static Set<QualifyingEvent> fromBytes(byte[] bytes) {
        JSONObject json = null;
        JSONArray array = null;
        try {
            json = new JSONObject(new String(bytes));
            array = json.getJSONArray("events");
            Set<QualifyingEvent> events = new HashSet<>();
            for (int i=0; i<array.length(); i++) {
                events.add(QualifyingEvent.valueOf(array.getString(i)));
            }
            return events;
        } catch (JSONException e) {
            Timber.e(e);
            return null;
        }
    }
}
