package com.jarvis.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Affiche la notification quand un rappel arrive à l'heure. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "jarvis_rappels";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String id = intent.getStringExtra("id");
        String text = intent.getStringExtra("text");
        if (id == null) return;
        if (text == null || text.isEmpty()) text = "Rappel";

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Rappels Jarvis", NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            ch.setDescription("Rappels et rendez-vous créés avec Jarvis");
            nm.createNotificationChannel(ch);
        }

        PendingIntent open = PendingIntent.getActivity(
                ctx, 0, new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(ctx, CHANNEL)
                : new Notification.Builder(ctx).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Jarvis")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true);
        try {
            nm.notify(id.hashCode(), b.build());
        } catch (SecurityException ignored) {
            // Autorisation de notification refusée.
        }
        Reminders.forget(ctx, id);
    }
}
