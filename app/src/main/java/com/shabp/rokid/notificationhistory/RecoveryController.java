package com.shabp.rokid.notificationhistory;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

/** Opt-in recovery using a one-time ADB grant; never starts or exposes an ADB server. */
final class RecoveryController {
    private static final String TAG = "NotificationRecovery";
    private static final String PREFS = "recovery";
    private static final String ENABLED = "enabled";

    static boolean optedIn(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false);
    }

    static void setOptedIn(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(ENABLED, enabled).apply();
        if (enabled) repair(context);
    }

    static boolean hasGrant(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static String repair(Context context) {
        if (!optedIn(context)) return "Recovery off";
        if (!hasGrant(context)) return "ADB grant required";
        try {
            ComponentName component = new ComponentName(context, NotificationAccessibilityService.class);
            String service = component.flattenToString();
            String existing = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean present = false;
            if (!TextUtils.isEmpty(existing)) {
                TextUtils.SimpleStringSplitter parts = new TextUtils.SimpleStringSplitter(':');
                parts.setString(existing);
                while (parts.hasNext()) {
                    if (component.equals(ComponentName.unflattenFromString(parts.next()))) {
                        present = true;
                        break;
                    }
                }
            }
            if (!present) {
                String updated = TextUtils.isEmpty(existing) ? service : existing + ":" + service;
                if (!Settings.Secure.putString(context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)) {
                    return "Recovery write failed";
                }
            }
            if (!Settings.Secure.putInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1)) return "Recovery write failed";
            return present ? "Accessibility already registered" : "Accessibility restored";
        } catch (SecurityException e) {
            Log.w(TAG, "Secure settings grant unavailable", e);
            return "ADB grant required";
        } catch (RuntimeException e) {
            Log.w(TAG, "Accessibility repair failed", e);
            return "Recovery failed";
        }
    }

    private RecoveryController() { }
}
