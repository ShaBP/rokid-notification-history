package com.shabp.rokid.notificationhistory;

final class NotificationEntry {
    final long id;
    final String packageName;
    final String appName;
    final String title;
    final String body;
    final long postedAt;

    NotificationEntry(long id, String packageName, String appName, String title, String body, long postedAt) {
        this.id = id;
        this.packageName = packageName;
        this.appName = appName;
        this.title = title;
        this.body = body;
        this.postedAt = postedAt;
    }
}
