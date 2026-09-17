package com.shabp.rokid.notificationhistory;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NotificationAccessibilityService extends AccessibilityService {
    private LocalPairing pairing;
    private String lastSignature = "";
    private long lastSavedAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastTreeRetryAt;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityHealth.connected(this);
        DisplayWakeWatchdogService.start(this);
        notifyChanged();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityHealth.event(this);
        if (pairing == null) pairing = new LocalPairing(this);
        pairing.observe(event);
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (getPackageName().equals(pkg)) return;
        String lowerPkg = pkg.toLowerCase();
        if (lowerPkg.startsWith("com.shabp.")) return;
        boolean rokidSource = lowerPkg.contains("rokid") || lowerPkg.contains("sprite") ||
                lowerPkg.contains("systemui") || lowerPkg.contains("notification");
        if (!rokidSource) return;
        recordDiagnostic("last_source", pkg + " type=" + event.getEventType());

        Set<String> text = new LinkedHashSet<>();
        boolean directCountdown = containsCountdown(event.getText()) ||
                isCountdownText(event.getContentDescription());
        for (CharSequence item : event.getText()) add(text, item);
        add(text, event.getContentDescription());

        Object data = event.getParcelableData();
        boolean nativeNotification = data instanceof Notification;
        if (data instanceof Notification) {
            Bundle extras = ((Notification) data).extras;
            if (extras != null) {
                add(text, extras.getCharSequence(Notification.EXTRA_TITLE));
                add(text, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
                add(text, extras.getCharSequence(Notification.EXTRA_TEXT));
            }
        }

        // Prefer the event's own text. Traversing the whole Sprite Launcher window can
        // sweep old launcher cards into the same event as a new notification.
        if (text.isEmpty()) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                directCountdown = containsCountdown(source, 0);
                collect(source, text, 0);
            }
        }
        if (text.isEmpty()) {
            reject("empty");
            return;
        }

        // Rokid renders notifications posted by apps installed on the glasses through
        // Sprite/SystemUI, so the Accessibility event package is not necessarily the
        // originating app package. Filter our local apps by the rendered app label too.
        if (isLocalGlassesAppNotification(text)) {
            reject("local_app");
            return;
        }

        // Accept a real Android Notification payload, or a Rokid mirrored popup carrying
        // either its notification header or its live countdown.
        if (!nativeNotification && !hasMirroredNotificationMarker(text) && !directCountdown) {
            reject("partial_no_marker");
            scheduleTreeRetry(pkg);
            return;
        }

        save(pkg, text);
    }

    private void save(String pkg, Set<String> text) {

        List<String> values = new ArrayList<>(text);
        String title = stripLeadingCountdown(values.get(0));
        String body = values.size() > 1 ?
                stripLeadingCountdown(TextUtils.join(" · ", values.subList(1, values.size()))) : "";
        String signature = normalizeCountdown(pkg + "|" + title + "|" + body);
        long now = System.currentTimeMillis();
        if (signature.equals(lastSignature) && now - lastSavedAt < 15000L) return;
        lastSignature = signature;
        lastSavedAt = now;

        HistoryStore store = new HistoryStore(this);
        store.saveRokid(pkg, shortName(pkg), title, body, now);
        store.close();
        recordDiagnostic("last_accepted", pkg + " @" + now);
        notifyChanged();
    }

    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent intent) {
        AccessibilityHealth.disconnected(this);
        notifyChanged();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        AccessibilityHealth.disconnected(this);
        notifyChanged();
        super.onDestroy();
    }

    private void scheduleTreeRetry(String fallbackPackage) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTreeRetryAt < 250L) return;
        lastTreeRetryAt = now;
        handler.postDelayed(() -> {
            if (getWindows() == null) return;
            for (android.view.accessibility.AccessibilityWindowInfo window : getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                String pkg = root.getPackageName() == null ? fallbackPackage :
                        root.getPackageName().toString();
                String lower = pkg.toLowerCase();
                if (!(lower.contains("rokid") || lower.contains("sprite") ||
                        lower.contains("systemui") || lower.contains("notification"))) continue;
                Set<String> values = new LinkedHashSet<>();
                boolean countdown = containsCountdown(root, 0);
                collect(root, values, 0);
                if (!values.isEmpty() && (countdown || hasMirroredNotificationMarker(values))) {
                    recordDiagnostic("last_tree_retry", "accepted " + pkg);
                    save(pkg, values);
                    return;
                }
            }
            reject("tree_retry_failed");
        }, 180L);
    }

    private void reject(String reason) {
        android.content.SharedPreferences prefs = getSharedPreferences(
                "accessibility_diagnostics", MODE_PRIVATE);
        prefs.edit().putString("last_rejection", reason + " @" + System.currentTimeMillis())
                .putInt("rejected_" + reason, prefs.getInt("rejected_" + reason, 0) + 1).apply();
    }

    private void recordDiagnostic(String key, String value) {
        getSharedPreferences("accessibility_diagnostics", MODE_PRIVATE).edit()
                .putString(key, value).apply();
    }

    private void notifyChanged() {
        sendBroadcast(new Intent(NotificationCollectorService.ACTION_HISTORY_CHANGED)
                .setPackage(getPackageName()));
    }

    private static boolean hasMirroredNotificationMarker(Set<String> values) {
        for (String value : values) {
            if (value.matches("(?is).*\\bnotifications?\\s*[|]\\s*\\S+.*")) return true;
        }
        return false;
    }

    private static boolean isLocalGlassesAppNotification(Set<String> values) {
        for (String value : values) {
            String normalized = value.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
            if (normalized.matches("^(?:notification )?(?:vesc hud|smart ?cam|" +
                    "notification history)(?: .*)?$")) return true;
        }
        return false;
    }

    private static boolean containsCountdown(List<CharSequence> values) {
        if (values == null) return false;
        for (CharSequence value : values) if (isCountdownText(value)) return true;
        return false;
    }

    private static boolean containsCountdown(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 8) return false;
        if (isCountdownText(node.getText()) || isCountdownText(node.getContentDescription())) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsCountdown(node.getChild(i), depth + 1)) return true;
        }
        return false;
    }

    private static boolean isCountdownText(CharSequence value) {
        if (value == null) return false;
        return value.toString().trim().matches("(?i)^(?:[0-9]|[12][0-9]|30)\\s*" +
                "(?:s|sec|secs|second|seconds)$");
    }

    private static void collect(AccessibilityNodeInfo node, Set<String> result, int depth) {
        if (node == null || depth > 8 || result.size() >= 24) return;
        add(result, node.getText());
        add(result, node.getContentDescription());
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), result, depth + 1);
    }

    private static void add(Set<String> result, CharSequence value) {
        if (value == null) return;
        String clean = value.toString().replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (clean.matches("(?i)^(?:[0-9]|[12][0-9]|30)\\s*(?:s|sec|secs|second|seconds)?$")) return;
        if (!clean.isEmpty() && clean.length() <= 500) result.add(clean);
    }

    private static String normalizeCountdown(String value) {
        return value.replaceAll("(?i)\\b(?:in\\s+)?(?:[0-9]|[12][0-9]|30)\\s*" +
                "(?:s|sec|secs|second|seconds)\\b", "<countdown>")
                .replaceAll("\\s+", " ").trim();
    }

    private static String stripLeadingCountdown(String value) {
        return value.replaceFirst("(?i)^(?:[0-9]|[12][0-9]|30)\\s*" +
                "(?:s|sec|secs|second|seconds)\\s*(?:[·|:–—-]\\s*)?", "").trim();
    }

    private String shortName(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
            int dot = pkg.lastIndexOf('.');
            return dot >= 0 ? pkg.substring(dot + 1) : pkg;
        }
    }
}
