package com.jarvis.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = 0xFF08141A;
    private static final int SURFACE = 0xFF0F2129;
    private static final int SURFACE2 = 0xFF153039;
    private static final int LINE = 0xFF1F4450;
    private static final int FG = 0xFFE4F4F7;
    private static final int MUTED = 0xFF8FB0B8;
    private static final int ACCENT = 0xFF4FD6E6;
    private static final int ACCENT_INK = 0xFF032329;
    private static final int DANGER = 0xFFFF7A70;
    private static final int DANGER_INK = 0xFF3A0A06;

    private static final int REQ_MIC = 1;
    private static final int REQ_NOTIF = 2;

    private LinearLayout log;
    private ScrollView scroll;
    private EditText input;
    private Button micBtn, sendBtn, voiceBtn, callBtn;
    private TextView status;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer recognizer;
    private JarvisBrain brain;
    private SharedPreferences prefs;
    private boolean voiceOn = true;
    private boolean busy = false;
    private boolean listening = false;
    private boolean callMode = false;
    private boolean pendingCall = false;
    private int misses = 0;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable listenRunnable = new Runnable() {
        public void run() {
            if (callMode && !busy && !listening) beginListening();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("jarvis", 0);
        voiceOn = prefs.getBoolean("voice", true);
        brain = new JarvisBrain(this);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int s) {
                if (s == TextToSpeech.SUCCESS) {
                    int r = tts.setLanguage(Locale.FRANCE);
                    ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String id) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    setStatus("Je parle…");
                                }
                            });
                        }

                        @Override
                        public void onDone(String id) {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    if (callMode) {
                                        setStatus("En appel");
                                        if (!busy) main.postDelayed(listenRunnable, 400);
                                    } else if (!busy) {
                                        setStatus("En veille");
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(String id) {
                            onDone(id);
                        }
                    });
                }
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }

        addBubble(false, "Bonjour, je suis Jarvis. Touche « Appel vocal » pour parler avec moi sans les mains, ou écris-moi. "
                + "Je cherche sur Internet, je te donne l'actualité, je crée des rappels, je règle des alarmes, j'ouvre des applications "
                + "et je prépare des appels, des SMS ou des commandes en ligne.");
        if (prefs.getString("apiKey", "").trim().isEmpty()) showSettings(true);
    }

    // ---------------------------------------------------------------- Interface

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private Button smallButton(String text) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setTextSize(13);
        bt.setTextColor(ACCENT);
        bt.setMinHeight(0);
        bt.setMinimumHeight(0);
        bt.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setStroke(dp(1), LINE);
        g.setCornerRadius(dp(8));
        bt.setBackground(g);
        return bt;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("JARVIS", 22, ACCENT, true);
        title.setLetterSpacing(0.2f);
        status = label("En veille", 13, MUTED, false);
        brand.addView(title);
        brand.addView(status);
        top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        voiceBtn = smallButton(voiceOn ? "Voix : oui" : "Voix : non");
        voiceBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                voiceOn = !voiceOn;
                prefs.edit().putBoolean("voice", voiceOn).apply();
                voiceBtn.setText(voiceOn ? "Voix : oui" : "Voix : non");
                if (!voiceOn && !callMode && tts != null) tts.stop();
            }
        });
        Button settings = smallButton("Réglages");
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showSettings(false);
            }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.leftMargin = dp(8);
        top.addView(voiceBtn, bl);
        top.addView(settings, bl);
        root.addView(top);

        scroll = new ScrollView(this);
        log = new LinearLayout(this);
        log.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(log, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sl.topMargin = dp(8);
        root.addView(scroll, sl);

        callBtn = new Button(this);
        callBtn.setAllCaps(false);
        callBtn.setTextSize(16);
        styleCall(false);
        callBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (callMode) endCall(true);
                else startCall();
            }
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        cl.topMargin = dp(8);
        root.addView(callBtn, cl);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.BOTTOM);

        micBtn = new Button(this);
        micBtn.setText("Parler");
        micBtn.setAllCaps(false);
        micBtn.setTextColor(ACCENT);
        micBtn.setBackground(round(SURFACE, LINE, 24));
        micBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (callMode) return;
                toggleListening();
            }
        });

        input = new EditText(this);
        input.setHint("Écris ton message");
        input.setHintTextColor(MUTED);
        input.setTextColor(FG);
        input.setTextSize(16);
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackground(round(SURFACE, LINE, 14));
        input.setPadding(dp(14), dp(10), dp(14), dp(10));

        sendBtn = new Button(this);
        sendBtn.setText("Envoyer");
        sendBtn.setAllCaps(false);
        sendBtn.setTextColor(ACCENT_INK);
        sendBtn.setBackground(round(ACCENT, ACCENT, 24));
        sendBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                send(input.getText().toString());
            }
        });

        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ip.leftMargin = dp(8);
        ip.rightMargin = dp(8);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        bar.addView(micBtn, mp);
        bar.addView(input, ip);
        bar.addView(sendBtn, sp);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = dp(8);
        root.addView(bar, barLp);

        setContentView(root);
    }

    private void styleCall(boolean active) {
        callBtn.setText(active ? "Raccrocher" : "Appel vocal");
        callBtn.setTextColor(active ? DANGER_INK : ACCENT);
        callBtn.setBackground(active ? round(DANGER, DANGER, 24) : round(SURFACE, ACCENT, 24));
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView addBubble(boolean user, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTextColor(FG);
        t.setTextIsSelectable(true);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(user ? round(SURFACE2, LINE, 14) : round(SURFACE, ACCENT, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = user ? Gravity.END : Gravity.START;
        lp.topMargin = dp(5);
        lp.bottomMargin = dp(5);
        if (user) lp.leftMargin = dp(48);
        else lp.rightMargin = dp(48);
        log.addView(t, lp);
        scrollDown();
        return t;
    }

    private void scrollDown() {
        scroll.post(new Runnable() {
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    // ---------------------------------------------------------------- Appel vocal

    private void startCall() {
        if (prefs.getString("apiKey", "").trim().isEmpty()) {
            showSettings(true);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "La reconnaissance vocale n'est pas disponible sur ce téléphone.", Toast.LENGTH_LONG).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCall = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        callMode = true;
        misses = 0;
        brain.setVoiceCall(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        styleCall(true);
        setStatus("En appel");
        speak("Oui, je t'écoute.");
    }

    private void endCall(boolean sayBye) {
        if (!callMode) return;
        callMode = false;
        brain.setVoiceCall(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        styleCall(false);
        main.removeCallbacks(listenRunnable);
        try {
            if (recognizer != null) recognizer.cancel();
        } catch (Exception ignored) {
        }
        listening = false;
        micBtn.setText("Parler");
        if (tts != null) tts.stop();
        if (sayBye && tts != null && ttsReady) tts.speak("À bientôt.", TextToSpeech.QUEUE_FLUSH, null, "bye");
        if (!busy) setStatus("En veille");
    }

    private boolean isHangup(String t) {
        String s = t.toLowerCase(Locale.FRANCE).trim();
        if (s.length() > 40) return false;
        return s.contains("raccroche") || s.contains("au revoir") || s.contains("à bientôt") || s.contains("a bientôt")
                || s.contains("fin de l'appel") || s.contains("termine l'appel") || s.equals("stop");
    }

    // ---------------------------------------------------------------- Envoi

    private void send(String raw) {
        final String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || busy) return;
        if (callMode && isHangup(text)) {
            addBubble(true, text);
            endCall(true);
            return;
        }
        if (prefs.getString("apiKey", "").trim().isEmpty()) {
            endCall(false);
            showSettings(true);
            return;
        }
        if (tts != null) tts.stop();
        busy = true;
        sendBtn.setEnabled(false);
        input.setText("");
        addBubble(true, text);
        final TextView pending = addBubble(false, "…");
        setStatus("Je réfléchis…");

        exec.execute(new Runnable() {
            public void run() {
                JarvisBrain.Result r = null;
                String err = null;
                try {
                    r = brain.ask(text);
                } catch (Exception e) {
                    err = JarvisBrain.explain(e);
                }
                final JarvisBrain.Result res = r;
                final String error = err;
                runOnUiThread(new Runnable() {
                    public void run() {
                        busy = false;
                        sendBtn.setEnabled(true);
                        if (error != null) {
                            pending.setText(error);
                            scrollDown();
                            speak(error);
                            return;
                        }
                        pending.setText(res.sources == null || res.sources.isEmpty() ? res.text : res.text + "\n\n" + res.sources);
                        scrollDown();
                        speak(res.text);
                        final ArrayList<Runnable> acts = new ArrayList<Runnable>(res.actions);
                        if (!acts.isEmpty()) {
                            main.postDelayed(new Runnable() {
                                public void run() {
                                    for (Runnable a : acts) a.run();
                                }
                            }, 2500);
                        }
                    }
                });
            }
        });
    }

    private void speak(String text) {
        boolean want = voiceOn || callMode;
        if (!want || tts == null || !ttsReady) {
            setStatus(callMode ? "En appel" : "En veille");
            if (callMode) main.postDelayed(listenRunnable, 500);
            return;
        }
        tts.speak(text.replaceAll("[*_#`]", ""), TextToSpeech.QUEUE_FLUSH, null, "jarvis");
    }

    // ---------------------------------------------------------------- Micro

    private void toggleListening() {
        if (listening) {
            if (recognizer != null) recognizer.stopListening();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "La reconnaissance vocale n'est pas disponible sur ce téléphone. Utilise le micro du clavier.", Toast.LENGTH_LONG).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (tts != null) tts.stop();
        beginListening();
    }

    private void beginListening() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle p) {
                    listening = true;
                    micBtn.setText(callMode ? "…" : "Stop");
                    setStatus(callMode ? "En appel : je t'écoute" : "Je t'écoute…");
                }

                public void onBeginningOfSpeech() {
                }

                public void onRmsChanged(float v) {
                }

                public void onBufferReceived(byte[] b) {
                }

                public void onEndOfSpeech() {
                }

                public void onError(int code) {
                    listening = false;
                    micBtn.setText("Parler");
                    boolean silent = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            || code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;
                    if (callMode) {
                        if (silent && ++misses < 4) {
                            main.postDelayed(listenRunnable, 600);
                            return;
                        }
                        Toast.makeText(MainActivity.this,
                                silent ? "Je n'entends plus rien, j'ai raccroché." : "Problème de reconnaissance vocale, appel terminé.",
                                Toast.LENGTH_LONG).show();
                        endCall(false);
                        return;
                    }
                    setStatus("En veille");
                    if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Toast.makeText(MainActivity.this, "Je n'ai rien entendu. Réessaie.", Toast.LENGTH_SHORT).show();
                    } else if (code == SpeechRecognizer.ERROR_NETWORK || code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        Toast.makeText(MainActivity.this, "La reconnaissance vocale a besoin d'internet.", Toast.LENGTH_SHORT).show();
                    }
                }

                public void onResults(Bundle r) {
                    listening = false;
                    micBtn.setText("Parler");
                    misses = 0;
                    ArrayList<String> list = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) send(list.get(0));
                    else if (callMode) main.postDelayed(listenRunnable, 600);
                    else setStatus("En veille");
                }

                public void onPartialResults(Bundle r) {
                    ArrayList<String> list = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) input.setText(list.get(0));
                }

                public void onEvent(int t, Bundle p) {
                }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            listening = false;
            if (callMode) endCall(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_MIC) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                if (pendingCall) {
                    pendingCall = false;
                    startCall();
                } else {
                    toggleListening();
                }
            } else {
                pendingCall = false;
                Toast.makeText(this, "Sans l'autorisation du micro, utilise le micro du clavier.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------------------------------------------------------- Réglages

    private boolean notifAccess() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    private void openNotifAccess() {
        try {
            Toast.makeText(this, "Active « Jarvis » dans la liste, puis reviens à l'application.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Ouvre les paramètres Android, puis Notifications, puis Accès aux notifications.", Toast.LENGTH_LONG).show();
        }
    }

    private void showWaLog() {
        TextView t = new TextView(this);
        t.setText(AutoReplyService.readLog(this));
        t.setTextSize(14);
        t.setTextIsSelectable(true);
        t.setPadding(dp(20), dp(8), dp(20), dp(8));
        ScrollView s = new ScrollView(this);
        s.addView(t);
        new AlertDialog.Builder(this)
                .setTitle("Réponses envoyées")
                .setView(s)
                .setPositiveButton("Fermer", null)
                .setNeutralButton("Vider", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        AutoReplyService.clearLog(MainActivity.this);
                    }
                })
                .show();
    }

    private void showSettings(boolean first) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        TextView help = new TextView(this);
        help.setText("Jarvis utilise l'intelligence artificielle Claude. Crée une clé API sur console.anthropic.com (rubrique API keys), "
                + "ajoute un peu de crédit, puis colle la clé ici. Elle reste uniquement sur ce téléphone.");
        help.setTextSize(14);
        box.addView(help);

        final EditText key = new EditText(this);
        key.setHint("Clé API (sk-ant-...)");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(prefs.getString("apiKey", ""));
        box.addView(key);

        final EditText model = new EditText(this);
        model.setHint("Modèle (" + JarvisBrain.DEFAULT_MODEL + ")");
        model.setSingleLine(true);
        model.setText(prefs.getString("model", JarvisBrain.DEFAULT_MODEL));
        box.addView(model);

        TextView waHead = new TextView(this);
        waHead.setText("Réponses automatiques WhatsApp");
        waHead.setTextSize(16);
        waHead.setTypeface(waHead.getTypeface(), android.graphics.Typeface.BOLD);
        waHead.setPadding(0, dp(18), 0, dp(4));
        box.addView(waHead);

        TextView waHelp = new TextView(this);
        waHelp.setText("Quand c'est activé, Jarvis répond aux messages texte reçus sur WhatsApp (pas aux vocaux, photos ni appels). "
                + "Chaque réponse précise qu'elle vient d'un assistant automatique. Le texte des messages reçus est envoyé à Claude pour préparer la réponse. "
                + "Active-le seulement quand tu es absent.");
        waHelp.setTextSize(13);
        box.addView(waHelp);

        final Switch waSwitch = new Switch(this);
        waSwitch.setText("Jarvis répond à ma place");
        waSwitch.setChecked(prefs.getBoolean("wa_auto", false));
        waSwitch.setPadding(0, dp(8), 0, dp(4));
        box.addView(waSwitch);

        final CheckBox waGroups = new CheckBox(this);
        waGroups.setText("Répondre aussi dans les groupes");
        waGroups.setChecked(prefs.getBoolean("wa_groups", false));
        box.addView(waGroups);

        final EditText waOwner = new EditText(this);
        waOwner.setHint("Ton prénom");
        waOwner.setSingleLine(true);
        waOwner.setText(prefs.getString("wa_owner", ""));
        box.addView(waOwner);

        final EditText waRules = new EditText(this);
        waRules.setHint("Consignes (ex. : je suis occupé, prends un message)");
        waRules.setMinLines(2);
        waRules.setText(prefs.getString("wa_rules", ""));
        box.addView(waRules);

        TextView waAccess = new TextView(this);
        waAccess.setText(notifAccess() ? "Accès aux notifications : accordé" : "Accès aux notifications : non accordé");
        waAccess.setTextSize(13);
        waAccess.setPadding(0, dp(8), 0, 0);
        box.addView(waAccess);

        Button waGrant = new Button(this);
        waGrant.setText("Autoriser l'accès aux notifications");
        waGrant.setAllCaps(false);
        waGrant.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openNotifAccess();
            }
        });
        box.addView(waGrant);

        Button waLogBtn = new Button(this);
        waLogBtn.setText("Voir les réponses envoyées");
        waLogBtn.setAllCaps(false);
        waLogBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showWaLog();
            }
        });
        box.addView(waLogBtn);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        new AlertDialog.Builder(this)
                .setTitle(first ? "Bienvenue" : "Réglages")
                .setView(sv)
                .setPositiveButton("Enregistrer", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        prefs.edit()
                                .putString("apiKey", key.getText().toString().trim())
                                .putString("model", model.getText().toString().trim())
                                .putBoolean("wa_auto", waSwitch.isChecked())
                                .putBoolean("wa_groups", waGroups.isChecked())
                                .putString("wa_owner", waOwner.getText().toString().trim())
                                .putString("wa_rules", waRules.getText().toString().trim())
                                .apply();
                        if (waSwitch.isChecked() && !notifAccess()) openNotifAccess();
                    }
                })
                .setNeutralButton("Effacer la discussion", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        brain.clear();
                        log.removeAllViews();
                        addBubble(false, "Discussion effacée. Je t'écoute.");
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        endCall(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(listenRunnable);
        if (tts != null) tts.shutdown();
        if (recognizer != null) recognizer.destroy();
        exec.shutdownNow();
    }
}
