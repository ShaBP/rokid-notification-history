package com.shabp.rokid.notificationhistory;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.Collections;

public final class MainActivity extends Activity {
    private NotificationHistoryView historyView;
    private boolean onboardingCompleted;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { reload(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        historyView = new NotificationHistoryView(this);
        historyView.setOnEnableAccess(new Runnable() {
            @Override public void run() { openRequiredAccess(); }
        });
        historyView.setOnClearHistory(new Runnable() {
            @Override public void run() { clearHistory(); }
        });
        historyView.setOnOpenDeveloperSettings(new Runnable() {
            @Override public void run() { openDeveloperSettings(); }
        });
        onboardingCompleted = getPreferences(MODE_PRIVATE)
                .getBoolean("accessibility_onboarding_completed", false);
        setContentView(historyView);
        RokidNotificationBridge.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(NotificationCollectorService.ACTION_HISTORY_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    protected void onStop() {
        unregisterReceiver(receiver);
        super.onStop();
    }

    private void reload() {
        boolean enabled = isListenerEnabled();
        boolean accessibilityEnabled = isAccessibilityEnabled();
        if (accessibilityEnabled && !onboardingCompleted) {
            onboardingCompleted = true;
            getPreferences(MODE_PRIVATE).edit()
                    .putBoolean("accessibility_onboarding_completed", true).apply();
        }
        HistoryStore store = new HistoryStore(this);
        historyView.setData(store.load(), enabled, accessibilityEnabled,
                RokidNotificationBridge.getStatus(), !onboardingCompleted);
        store.close();
    }

    private void clearHistory() {
        HistoryStore store = new HistoryStore(this);
        store.clearHistory();
        store.close();
        reload();
    }

    private void openDeveloperSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception unavailable) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                Toast.makeText(this, "Open Developer options, then Wireless debugging",
                        Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
                Toast.makeText(this, "Android settings unavailable on these glasses",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        ComponentName component = new ComponentName(this, NotificationCollectorService.class);
        return flat != null && flat.contains(component.flattenToString());
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName component = new ComponentName(this, NotificationAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName item = ComponentName.unflattenFromString(splitter.next());
            if (component.equals(item)) return true;
        }
        return false;
    }

    private void openRequiredAccess() {
        if (!isAccessibilityEnabled()) {
            openAccessibilityAccess();
            return;
        }
        if (!isListenerEnabled()) {
            openNotificationAccess();
            return;
        }
        if (!isAccessibilityEnabled()) {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception ignored) { }
            return;
        }
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (Exception ignored) { }
    }

    private void openAccessibilityAccess() {
        ComponentName component = new ComponentName(this, NotificationAccessibilityService.class);
        try {
            Intent detail = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
            detail.setData(Uri.parse("package:" + getPackageName()));
            detail.putExtra("android.intent.extra.COMPONENT_NAME",
                    component.flattenToString());
            startActivity(detail);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception ignored) { }
        }
    }

    private void openNotificationAccess() {
        try {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            detail.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new ComponentName(this, NotificationCollectorService.class).flattenToString());
            startActivity(detail);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (Exception ignored) {
                historyView.setData(Collections.<NotificationEntry>emptyList(), false, false,
                        RokidNotificationBridge.getStatus(), !onboardingCompleted);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (historyView.handleKey(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }
}
