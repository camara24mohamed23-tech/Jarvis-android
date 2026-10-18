package com.jarvis.assistant;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.AlarmClock;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Le cerveau de Jarvis : parle à l'API Claude et exécute les actions sur le téléphone. */
public class JarvisBrain {
    static final String DEFAULT_MODEL = "claude-haiku-5-5";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    static class ApiException extends Exception {
        final int code;

        ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    static class Result {
        String text;
        List<Runnable> actions = new ArrayList<>();
    }

    private final Activity act;
    private final List<String[]> history = new ArrayList<>(); // paires {rôle, texte}
    private List<Runnable> actions = new ArrayList<>();

    JarvisBrain(Activity a) {
        act = a;
    }

    void clear() {
        history.clear();
    }

    /** À appeler hors du fil principal. */
    Result ask(String userText) throws Exception {
        SharedPreferences p = act.getSharedPreferences("jarvis", 0);
        String key = p.getString("apiKey", "").trim();
        String model = p.getString("model", DEFAULT_MODEL).trim();
        if (model.isEmpty()) model = DEFAULT_MODEL;

        actions = new ArrayList<>();
        JSONArray msgs = new JSONArray();
        int from = Math.max(0, history.size() - 20);
        for (int i = from; i < history.size(); i++) {
            msgs.put(new JSONObject().put("role", history.get(i)[0]).put("content", history.get(i)[1]));
        }
        msgs.put(new JSONObject().put("role", "user").put("content", userText));

        JSONArray tools = toolDefs();
        String system = systemPrompt();
        String finalText = "";

        for (int round = 0; round < 6; round++) {
            JSONObject body = new JSONObject()
                    .put("model", model)
                    .put("max_tokens", 1024)
                    .put("system", system)
                    .put("tools", tools)
                    .put("messages", msgs);
            JSONObject resp = post(key, body);
            JSONArray content = resp.getJSONArray("content");

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject b = content.getJSONObject(i);
                if ("text".equals(b.optString("type"))) sb.append(b.optString("text"));
            }
            finalText = sb.toString().trim();

            if (!"tool_use".equals(resp.optString("stop_reason"))) break;

            msgs.put(new JSONObject().put("role", "assistant").put("content", content));
            JSONArray results = new JSONArray();
            for (int i = 0; i < content.length(); i++) {
                JSONObject b = content.getJSONObject(i);
                if (!"tool_use".equals(b.optString("type"))) continue;
                JSONObject r = new JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", b.getString("id"));
                try {
                    r.put("content", execTool(b.getString("name"), b.optJSONObject("input")));
                } catch (Exception e) {
                    r.put("content", "Erreur : " + e.getMessage());
                    r.put("is_error", true);
                }
                results.put(r);
            }
            msgs.put(new JSONObject().put("role", "user").put("content", results));
        }

        if (finalText.isEmpty()) finalText = "C'est fait.";
        history.add(new String[]{"user", userText});
        history.add(new String[]{"assistant", finalText});

        Result res = new Result();
        res.text = finalText;
        res.actions = actions;
        return res;
    }

    // ------------------------------------------------------------------ API

    private JSONObject post(String key, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API_URL).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setRequestProperty("content-type", "application/json");
        c.setRequestProperty("x-api-key", key);
        c.setRequestProperty("anthropic-version", "2023-06-01");
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int code = c.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String s = readAll(is);
        if (code < 200 || code >= 300) {
            String msg = s;
            try {
                msg = new JSONObject(s).getJSONObject("error").getString("message");
            } catch (Exception ignored) {
            }
            throw new ApiException(code, msg);
        }
        return new JSONObject(s);
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
        is.close();
        return out.toString("UTF-8");
    }

    /** Message lisible pour l'utilisateur à partir d'une erreur. */
    static String explain(Exception e) {
        if (e instanceof ApiException) {
            ApiException a = (ApiException) e;
            if (a.code == 401) return "La clé API est refusée. Vérifie-la dans Réglages.";
            if (a.code == 404) return "Ce modèle n'existe pas. Vérifie son nom dans Réglages.";
            if (a.code == 429) return "Trop de demandes d'un coup. Réessaie dans un instant.";
            if (a.code == 529 || a.code >= 500) return "Le service Claude est surchargé. Réessaie dans un instant.";
            return "Erreur de l'API : " + a.getMessage();
        }
        if (e instanceof java.io.IOException) return "Pas de connexion internet.";
        return "Je n'ai pas pu répondre (" + e.getClass().getSimpleName() + ").";
    }

    // --------------------------------------------------------------- Consignes

    private String systemPrompt() {
        String now = new SimpleDateFormat("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRANCE).format(new Date());
        String iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new Date());
        return "Tu es JARVIS, l'assistant personnel de poche de l'utilisateur, installé sur son téléphone Android. "
                + "Réponds toujours en français, de façon chaleureuse, claire et brève (1 à 4 phrases), car ta réponse est lue à voix haute. "
                + "Pas d'emojis, pas de titres, pas de markdown.\n"
                + "Date et heure actuelles : " + now + " (heure locale " + iso + ").\n"
                + "Tu disposes d'outils pour agir sur le téléphone : rappels et rendez-vous (creer_rappel, lister_rappels, supprimer_rappel), "
                + "alarmes et minuteurs (regler_alarme, minuteur), appels et SMS (appeler, ecrire_sms), "
                + "ouverture d'applications et de liens (ouvrir_application, ouvrir_lien), heure_actuelle. "
                + "Utilise-les dès que la demande s'y prête, puis confirme en une phrase.\n"
                + "Les actions d'ouverture, d'appel, de SMS et d'alarme s'exécutent juste après ta réponse. "
                + "Pour un appel ou un SMS, l'utilisateur valide lui-même sur l'écran du téléphone : ne dis jamais que l'appel est passé ou le SMS envoyé, "
                + "dis que c'est prêt. Tu ne peux pas lire les contacts : demande le numéro si tu ne l'as pas.\n"
                + "Si une demande est ambiguë (heure ou numéro manquant), pose une seule question courte.";
    }

    // ------------------------------------------------------------------ Outils

    private static JSONObject prop(String type, String desc) throws Exception {
        return new JSONObject().put("type", type).put("description", desc);
    }

    private static JSONObject tool(String name, String desc, JSONObject props, String... required) throws Exception {
        JSONObject schema = new JSONObject().put("type", "object").put("properties", props);
        JSONArray req = new JSONArray();
        for (String r : required) req.put(r);
        if (req.length() > 0) schema.put("required", req);
        return new JSONObject().put("name", name).put("description", desc).put("input_schema", schema);
    }

    private JSONArray toolDefs() throws Exception {
        JSONArray t = new JSONArray();
        t.put(tool("heure_actuelle", "Donne la date et l'heure actuelles.", new JSONObject()));
        t.put(tool("creer_rappel",
                "Crée un rappel ou un rendez-vous qui déclenchera une notification sonore. date_heure au format AAAA-MM-JJTHH:MM, heure locale, dans le futur.",
                new JSONObject().put("texte", prop("string", "Ce dont il faut se souvenir"))
                        .put("date_heure", prop("string", "AAAA-MM-JJTHH:MM")),
                "texte", "date_heure"));
        t.put(tool("lister_rappels", "Liste les rappels et rendez-vous à venir.", new JSONObject()));
        t.put(tool("supprimer_rappel", "Supprime un rappel à partir de son id (voir lister_rappels).",
                new JSONObject().put("id", prop("string", "Identifiant du rappel")), "id"));
        t.put(tool("regler_alarme", "Règle une alarme de réveil dans l'application Horloge du téléphone.",
                new JSONObject().put("heure", prop("integer", "Heure, de 0 à 23"))
                        .put("minute", prop("integer", "Minute, de 0 à 59"))
                        .put("libelle", prop("string", "Nom de l'alarme (facultatif)")),
                "heure", "minute"));
        t.put(tool("minuteur", "Lance un minuteur dans l'application Horloge.",
                new JSONObject().put("secondes", prop("integer", "Durée en secondes"))
                        .put("libelle", prop("string", "Nom du minuteur (facultatif)")),
                "secondes"));
        t.put(tool("appeler", "Prépare un appel : ouvre le clavier du téléphone avec le numéro. L'utilisateur appuie lui-même sur appeler.",
                new JSONObject().put("numero", prop("string", "Numéro de téléphone, avec indicatif si possible")), "numero"));
        t.put(tool("ecrire_sms", "Prépare un SMS : ouvre l'application de messages avec le texte. L'utilisateur appuie lui-même sur envoyer.",
                new JSONObject().put("numero", prop("string", "Numéro du destinataire"))
                        .put("message", prop("string", "Texte du SMS")),
                "numero", "message"));
        t.put(tool("ouvrir_application", "Ouvre une application installée sur le téléphone à partir de son nom (WhatsApp, Caméra, Chrome, YouTube...).",
                new JSONObject().put("nom", prop("string", "Nom de l'application")), "nom"));
        t.put(tool("ouvrir_lien", "Ouvre une adresse https (site web, Google Maps, YouTube, https://wa.me/NUMERO pour WhatsApp) ou une position geo:.",
                new JSONObject().put("url", prop("string", "Adresse https:// ou geo:")), "url"));
        return t;
    }

    private static String ok() throws Exception {
        return new JSONObject().put("ok", true).toString();
    }

    private void later(final Intent i, final String failMsg) {
        actions.add(new Runnable() {
            @Override
            public void run() {
                try {
                    act.startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(act, failMsg, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(act, failMsg, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private String execTool(String name, JSONObject in) throws Exception {
        if (in == null) in = new JSONObject();
        SimpleDateFormat show = new SimpleDateFormat("EEE d MMM 'à' HH:mm", Locale.FRANCE);

        switch (name) {
            case "heure_actuelle":
                return new JSONObject().put("maintenant",
                        new SimpleDateFormat("EEEE d MMMM yyyy HH:mm", Locale.FRANCE).format(new Date())).toString();

            case "creer_rappel": {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                f.setLenient(false);
                long at;
                try {
                    at = f.parse(in.getString("date_heure")).getTime();
                } catch (Exception e) {
                    throw new Exception("Date invalide, utilise AAAA-MM-JJTHH:MM");
                }
                if (at <= System.currentTimeMillis()) throw new Exception("Cette date est déjà passée");
                String text = in.getString("texte");
                if (text.length() > 200) text = text.substring(0, 200);
                String id = Reminders.add(act, text, at);
                return new JSONObject().put("ok", true).put("id", id).put("pour_le", show.format(new Date(at))).toString();
            }

            case "lister_rappels": {
                JSONArray a = Reminders.load(act);
                JSONArray out = new JSONArray();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    out.put(new JSONObject().put("id", o.getString("id")).put("texte", o.getString("text"))
                            .put("quand", show.format(new Date(o.getLong("at")))));
                }
                return out.toString();
            }

            case "supprimer_rappel":
                return new JSONObject().put("ok", Reminders.remove(act, in.getString("id"))).toString();

            case "regler_alarme": {
                int h = in.getInt("heure");
                int m = in.getInt("minute");
                if (h < 0 || h > 23 || m < 0 || m > 59) throw new Exception("Heure invalide");
                Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, h)
                        .putExtra(AlarmClock.EXTRA_MINUTES, m)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, in.optString("libelle", "Jarvis"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                later(i, "Aucune application Horloge trouvée.");
                return ok();
            }

            case "minuteur": {
                int s = in.getInt("secondes");
                if (s < 1 || s > 86400) throw new Exception("Durée invalide");
                Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, s)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, in.optString("libelle", "Jarvis"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                later(i, "Aucune application Horloge trouvée.");
                return ok();
            }

            case "appeler": {
                String n = in.getString("numero").replaceAll("[^0-9+]", "");
                if (n.isEmpty()) throw new Exception("Numéro invalide");
                later(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + n)), "Impossible d'ouvrir le clavier d'appel.");
                return ok();
            }

            case "ecrire_sms": {
                String n = in.getString("numero").replaceAll("[^0-9+]", "");
                if (n.isEmpty()) throw new Exception("Numéro invalide");
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + n));
                i.putExtra("sms_body", in.getString("message"));
                later(i, "Impossible d'ouvrir les messages.");
                return ok();
            }

            case "ouvrir_application": {
                PackageManager pm = act.getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
                String q = in.getString("nom").toLowerCase(Locale.ROOT).trim();
                ResolveInfo best = null;
                for (ResolveInfo ri : apps) {
                    String label = ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
                    if (label.equals(q)) {
                        best = ri;
                        break;
                    }
                    if (best == null && (label.contains(q) || q.contains(label))) best = ri;
                }
                if (best == null) throw new Exception("Application introuvable : " + in.getString("nom"));
                Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
                if (launch == null) throw new Exception("Application impossible à ouvrir");
                later(launch, "Impossible d'ouvrir l'application.");
                return new JSONObject().put("ok", true).put("application", best.loadLabel(pm).toString()).toString();
            }

            case "ouvrir_lien": {
                String u = in.getString("url").trim();
                if (!(u.startsWith("https://") || u.startsWith("geo:"))) throw new Exception("Seules les adresses https:// et geo: sont acceptées");
                later(new Intent(Intent.ACTION_VIEW, Uri.parse(u)), "Impossible d'ouvrir ce lien.");
                return ok();
            }

            default:
                throw new Exception("Outil inconnu : " + name);
        }
    }
}
