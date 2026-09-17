package com.shabp.rokid.notificationhistory;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public final class DisplayWakeWatchdogService extends Service {
    private static final String CHANNEL = "notification_capture_watchdog";
    private static final int NOTIFICATION_ID = 1901;
    private static final long PERIODIC_CHECK_MS = 5 * 60 * 1000L;
    private static final long REBIND_COOLDOWN_MS = 60 * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastRebindElapsed;

    private final Runnable periodicCheck = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            long lastEvent = AccessibilityHealth.lastEventElapsed();
            boolean stale = lastEvent == 0L || now - lastEvent >= PERIODIC_CHECK_MS;
            PowerManager power = getSystemService(PowerManager.class);
            boolean displayOn = power != null && power.isInteractive();
            if (RecoveryController.optedIn(DisplayWakeWatchdogService.this) &&
                    RecoveryController.isRegistered(DisplayWakeWatchdogService.this) &&
                    (!AccessibilityHealth.connected() || (stale && !displayOn))) {
                rebind(!AccessibilityHealth.connected() ?
                        "Accessibility disconnected" : "periodic screen-off refresh");
            }
            handler.postDelayed(this, PERIODIC_CHECK_MS);
        }
    };

    static void start(Context context) {
        if (!RecoveryController.optedIn(context)) return;
        Intent intent = new Intent(context, DisplayWakeWatchdogService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException e) {
            Log.w("NotificationWatchdog", "Could not start display watchdog", e);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        handler.postDelayed(periodicCheck, 15_000L);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void rebind(String reason) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRebindElapsed < REBIND_COOLDOWN_MS) return;
        lastRebindElapsed = now;
        Log.w("NotificationWatchdog", reason + "; rebinding Accessibility");
        getSharedPreferences("accessibility_diagnostics", MODE_PRIVATE).edit()
                .putString("last_watchdog_rebind", reason + " @" + System.currentTimeMillis())
                .apply();
        RecoveryController.forceRebind(this);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "Notification capture reliability", NotificationManager.IMPORTANCE_MIN);
        channel.setDescription("Keeps notification capture ready when the glasses display wakes");
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, pendingFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("Notification History")
                .setContentText("Capture watchdog active")
                .setOngoing(true).setOnlyAlertOnce(true).setLocalOnly(true)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pending).build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
