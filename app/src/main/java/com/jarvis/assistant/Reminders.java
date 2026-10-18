package com.jarvis.assistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

/** Stockage des rappels et programmation des alarmes Android. */
public class Reminders {
    static final String PREFS = "jarvis";
    static final String KEY = "reminders";

    static synchronized JSONArray load(Context c) {
        try {
            return new JSONArray(c.getSharedPreferences(PREFS, 0).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(Context c, JSONArray a) {
        c.getSharedPreferences(PREFS, 0).edit().putString(KEY, a.toString()).apply();
    }

    static synchronized String add(Context c, String text, long at) throws JSONException {
        String id = Long.toString(Math.abs(new Random().nextLong()), 36);
        if (id.length() > 6) id = id.substring(0, 6);
        JSONArray a = load(c);
        a.put(new JSONObject().put("id", id).put("text", text).put("at", at));
        save(c, a);
        schedule(c, id, text, at);
        return id;
    }

    static synchronized boolean remove(Context c, String id) {
        JSONArray a = load(c);
        JSONArray out = new JSONArray();
        boolean found = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (id.equals(o.optString("id"))) {
                found = true;
            } else {
                out.put(o);
            }
        }
        save(c, out);
        if (found) cancel(c, id);
        return found;
    }

    /** Retire le rappel de la liste sans toucher à l'alarme (déjà déclenchée). */
    static synchronized void forget(Context c, String id) {
        JSONArray a = load(c);
        JSONArray out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) out.put(o);
        }
        save(c, out);
    }

    static synchronized void rescheduleAll(Context c) {
        JSONArray a = load(c);
        JSONArray keep = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            long at = o.optLong("at");
            if (at > now) {
                keep.put(o);
                schedule(c, o.optString("id"), o.optString("text"), at);
            }
        }
        save(c, keep);
    }

    private static PendingIntent pending(Context c, String id, String text, int flags) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setData(Uri.parse("jarvis://reminder/" + id));
        i.putExtra("id", id);
        i.putExtra("text", text);
        return PendingIntent.getBroadcast(c, id.hashCode(), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context c, String id, String text, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent show = PendingIntent.getActivity(
                c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent fire = pending(c, id, text, PendingIntent.FLAG_UPDATE_CURRENT);
        // setAlarmClock ne demande aucune autorisation spéciale et sonne à l'heure exacte.
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), fire);
    }

    private static void cancel(Context c, String id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pending(c, id, "", PendingIntent.FLAG_NO_CREATE);
        if (p != null) {
            am.cancel(p);
            p.cancel();
        }
    }
}
