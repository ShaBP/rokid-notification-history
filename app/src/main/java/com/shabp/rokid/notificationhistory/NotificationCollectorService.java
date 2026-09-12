package com.shabp.rokid.notificationhistory;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class NotificationCollectorService extends NotificationListenerService {
    static final String ACTION_HISTORY_CHANGED =
            "com.shabp.rokid.notificationhistory.HISTORY_CHANGED";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        store(sbn);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        RokidNotificationBridge.start(this);
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification sbn : active) store(sbn);
        } catch (SecurityException ignored) { }
    }

    private void store(StatusBarNotification sbn) {
        HistoryStore store = new HistoryStore(this);
        store.save(this, sbn);
        store.close();
        sendBroadcast(new Intent(ACTION_HISTORY_CHANGED).setPackage(getPackageName()));
    }
}
