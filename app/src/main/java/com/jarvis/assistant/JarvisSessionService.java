package com.jarvis.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

/** Appelé par Android quand l'utilisateur invoque l'assistant (appui long sur le bouton, geste, etc.). */
public class JarvisSessionService extends VoiceInteractionSessionService {
    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        return new JarvisSession(this);
    }

    static class JarvisSession extends VoiceInteractionSession {
        JarvisSession(Context c) {
            super(c);
        }

        @Override
        public void onShow(Bundle args, int showFlags) {
            super.onShow(args, showFlags);
            Intent i = new Intent(getContext(), MainActivity.class)
                    .setAction(Intent.ACTION_ASSIST)
                    .putExtra("autostart", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                startAssistantActivity(i);
            } catch (Exception e) {
                try {
                    getContext().startActivity(i);
                } catch (Exception ignored) {
                }
            }
            hide();
        }
    }
}
