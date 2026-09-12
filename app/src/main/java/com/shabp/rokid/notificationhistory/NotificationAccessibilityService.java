package com.shabp.rokid.notificationhistory;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NotificationAccessibilityService extends AccessibilityService {
    private String lastSignature = "";
    private long lastSavedAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (getPackageName().equals(pkg)) return;
        String lowerPkg = pkg.toLowerCase();
        if (lowerPkg.startsWith("com.shabp.")) return;
        boolean rokidSource = lowerPkg.contains("rokid") || lowerPkg.contains("sprite") ||
                lowerPkg.contains("systemui") || lowerPkg.contains("notification");
        if (!rokidSource) return;

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
        if (text.isEmpty()) return;

        // Accept a real Android Notification payload, or a Rokid mirrored popup carrying
        // either its notification header or its live countdown.
        if (!nativeNotification && !hasMirroredNotificationMarker(text) && !directCountdown) return;

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
        sendBroadcast(new Intent(NotificationCollectorService.ACTION_HISTORY_CHANGED)
                .setPackage(getPackageName()));
    }

    @Override public void onInterrupt() { }

    private static boolean hasMirroredNotificationMarker(Set<String> values) {
        for (String value : values) {
            if (value.matches("(?is).*\\bnotifications?\\s*[|]\\s*\\S+.*")) return true;
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
        if (node == null || depth > 8 || result.size() >= 12) return;
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
