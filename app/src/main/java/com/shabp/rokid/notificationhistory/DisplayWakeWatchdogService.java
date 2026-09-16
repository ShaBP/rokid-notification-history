package com.shabp.rokid.notificationhistory;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

public final class DisplayWakeWatchdogService extends Service {
    private static final String CHANNEL = "notification_capture_watchdog";
    private static final int NOTIFICATION_ID = 1901;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) probeAfterWake();
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
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        receiverRegistered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void probeAfterWake() {
        final long before = AccessibilityHealth.lastEventElapsed();
        handler.postDelayed(() -> {
            if (!RecoveryController.optedIn(this) || !RecoveryController.isRegistered(this)) return;
            boolean callbackAfterWake = AccessibilityHealth.lastEventElapsed() > before;
            if (!AccessibilityHealth.connected() || !callbackAfterWake) {
                Log.w("NotificationWatchdog", "Accessibility wake probe failed; rebinding");
                RecoveryController.forceRebind(this);
            }
        }, 1200L);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "Notification capture reliability", NotificationManager.IMPORTANCE_MIN);
        channel.setDescription("Keeps notification capture ready when the glasses display wakes");
        channel.setShowBadge(false);
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
                .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pending).build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) unregisterReceiver(screenReceiver);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
