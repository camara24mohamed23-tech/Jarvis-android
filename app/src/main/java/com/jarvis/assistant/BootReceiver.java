package com.jarvis.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reprogramme les rappels après un redémarrage du téléphone. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Reminders.rescheduleAll(ctx);
        }
    }
}
