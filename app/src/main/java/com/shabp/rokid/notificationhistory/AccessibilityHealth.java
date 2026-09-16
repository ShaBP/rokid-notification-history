package com.shabp.rokid.notificationhistory;

import android.content.Context;
import android.os.SystemClock;

final class AccessibilityHealth {
    private static volatile boolean connected;
    private static volatile long lastEventElapsed;
    private static volatile long lastPersistElapsed;

    static void connected(Context context) {
        connected = true;
        context.getSharedPreferences("accessibility_health", Context.MODE_PRIVATE).edit()
                .putLong("last_connected_wall", System.currentTimeMillis()).apply();
    }

    static void disconnected(Context context) {
        connected = false;
        context.getSharedPreferences("accessibility_health", Context.MODE_PRIVATE).edit()
                .putLong("last_disconnected_wall", System.currentTimeMillis()).apply();
    }

    static void event(Context context) {
        lastEventElapsed = SystemClock.elapsedRealtime();
        if (lastEventElapsed - lastPersistElapsed >= 5000L) {
            lastPersistElapsed = lastEventElapsed;
            context.getSharedPreferences("accessibility_health", Context.MODE_PRIVATE).edit()
                    .putLong("last_event_wall", System.currentTimeMillis()).apply();
        }
    }

    static boolean connected() { return connected; }
    static long lastEventElapsed() { return lastEventElapsed; }

    static String status(Context context, boolean registered) {
        if (!registered) return "ACCESSIBILITY OFF";
        if (connected) return "LISTENING";
        if (RecoveryController.optedIn(context)) return "REGISTERED / RECOVERING";
        return "REGISTERED / INACTIVE";
    }

    private AccessibilityHealth() { }
}
