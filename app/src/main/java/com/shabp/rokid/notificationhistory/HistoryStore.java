package com.shabp.rokid.notificationhistory;

import android.app.Notification;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

final class HistoryStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "notification_history.db";
    private static final int DB_VERSION = 1;
    private static final int MAX_HISTORY = 200;

    HistoryStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notifications (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "notification_key TEXT NOT NULL," +
                "package_name TEXT NOT NULL," +
                "app_name TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "posted_at INTEGER NOT NULL," +
                "UNIQUE(notification_key, posted_at) ON CONFLICT IGNORE)");
        db.execSQL("CREATE INDEX notification_time ON notifications(posted_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS notifications");
        onCreate(db);
    }

    void save(Context context, StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null ||
                context.getPackageName().equals(sbn.getPackageName())) return;

        Notification n = sbn.getNotification();
        Bundle extras = n.extras;
        String title = clean(extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = clean(extras == null ? null : extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (body.isEmpty()) body = clean(extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT));
        if (body.isEmpty() && extras != null) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) body = clean(TextUtils.join(" · ", lines));
        }
        if (title.isEmpty() && body.isEmpty()) return;

        String packageName = sbn.getPackageName();
        String appName = packageName;
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            appName = clean(context.getPackageManager().getApplicationLabel(info));
        } catch (Exception ignored) { }

        ContentValues values = new ContentValues();
        values.put("notification_key", sbn.getKey() == null ? packageName + ":" + sbn.getId() : sbn.getKey());
        values.put("package_name", packageName);
        values.put("app_name", appName);
        values.put("title", title);
        values.put("body", body);
        values.put("posted_at", sbn.getPostTime());
        SQLiteDatabase db = getWritableDatabase();
        db.insert("notifications", null, values);
        db.execSQL("DELETE FROM notifications WHERE _id NOT IN " +
                "(SELECT _id FROM notifications ORDER BY posted_at DESC LIMIT " + MAX_HISTORY + ")");
    }

    void saveRokid(String packageName, String appName, String title, String body, long postedAt) {
        packageName = clean(packageName);
        appName = clean(appName);
        title = clean(title);
        body = clean(body);
        if (title.isEmpty() && body.isEmpty()) return;
        if (packageName.isEmpty()) packageName = "rokid.mirrored.notification";
        if (appName.isEmpty()) appName = packageName;
        if (postedAt > 0 && postedAt < 100000000000L) postedAt *= 1000L;
        if (postedAt <= 0) postedAt = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.put("notification_key", "rokid:" + packageName + ":" + postedAt + ":" + title.hashCode());
        values.put("package_name", packageName);
        values.put("app_name", appName);
        values.put("title", title);
        values.put("body", body);
        values.put("posted_at", postedAt);
        SQLiteDatabase db = getWritableDatabase();
        db.insert("notifications", null, values);
        db.execSQL("DELETE FROM notifications WHERE _id NOT IN " +
                "(SELECT _id FROM notifications ORDER BY posted_at DESC LIMIT " + MAX_HISTORY + ")");
    }

    List<NotificationEntry> load() {
        List<NotificationEntry> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "notifications",
                new String[]{"_id", "package_name", "app_name", "title", "body", "posted_at"},
                null, null, null, null, "posted_at DESC", Integer.toString(MAX_HISTORY))) {
            while (cursor.moveToNext()) {
                result.add(new NotificationEntry(
                        cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getLong(5)));
            }
        }
        return result;
    }

    void clearHistory() {
        getWritableDatabase().delete("notifications", null, null);
    }

    private static String clean(CharSequence text) {
        if (text == null) return "";
        return text.toString().replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }
}
