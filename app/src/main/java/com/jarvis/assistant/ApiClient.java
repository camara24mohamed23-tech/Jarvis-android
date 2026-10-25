package com.jarvis.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Choisit comment parler à Claude : directement avec la clé API de l'utilisateur,
 * ou par le relais du propriétaire avec un code d'accès (sans clé).
 */
final class ApiClient {
    /** Adresse du relais, renseignée par défaut pour que les amis n'aient que le code à saisir. */
    static final String DEFAULT_PROXY = "";

    private ApiClient() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jarvis", 0);
    }

    static String proxyUrl(Context c) {
        String u = prefs(c).getString("proxyUrl", "").trim();
        if (u.isEmpty()) u = DEFAULT_PROXY;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    static boolean hasKey(Context c) {
        return !prefs(c).getString("apiKey", "").trim().isEmpty();
    }

    static boolean useProxy(Context c) {
        return !hasKey(c) && !prefs(c).getString("accessCode", "").trim().isEmpty() && !proxyUrl(c).isEmpty();
    }

    /** Vrai si l'application peut parler à Claude (clé API, ou code d'accès avec relais). */
    static boolean ready(Context c) {
        return hasKey(c) || useProxy(c);
    }

    static HttpURLConnection open(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        HttpURLConnection h;
        if (hasKey(c)) {
            h = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
            h.setRequestProperty("x-api-key", p.getString("apiKey", "").trim());
        } else {
            h = (HttpURLConnection) new URL(proxyUrl(c) + "/v1/messages").openConnection();
            h.setRequestProperty("x-camara-code", p.getString("accessCode", "").trim());
        }
        h.setRequestMethod("POST");
        h.setConnectTimeout(20000);
        h.setDoOutput(true);
        h.setRequestProperty("content-type", "application/json");
        h.setRequestProperty("anthropic-version", "2023-06-01");
        return h;
    }
}
