package com.shabp.rokid.notificationhistory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class NotificationHistoryView extends View {
    private static final int GREEN = Color.rgb(90, 255, 145);
    private static final int DIM_GREEN = Color.rgb(50, 155, 90);
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private List<NotificationEntry> entries = new ArrayList<>();
    private int selected = 0;
    private boolean listenerEnabled;
    private boolean accessibilityEnabled;
    private String rokidStatus = "START";
    private float touchStartY;
    private float touchStartX;
    private Runnable enableAccess;
    private Runnable clearHistory;
    private Runnable openDeveloperSettings;
    private boolean clearSelected;
    private boolean developerSelected;
    private boolean showOnboarding;

    NotificationHistoryView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    void setOnEnableAccess(Runnable action) { enableAccess = action; }
    void setOnClearHistory(Runnable action) { clearHistory = action; }
    void setOnOpenDeveloperSettings(Runnable action) { openDeveloperSettings = action; }

    void setData(List<NotificationEntry> newEntries, boolean enabled, boolean accessibility,
                 String status, boolean onboarding) {
        entries = newEntries == null ? new ArrayList<>() : newEntries;
        listenerEnabled = enabled;
        accessibilityEnabled = accessibility;
        rokidStatus = status == null ? "UNKNOWN" : status;
        showOnboarding = onboarding;
        selected = Math.max(0, Math.min(selected, entries.size() - 1));
        invalidate();
    }

    boolean handleKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_SPACE) {
            if (developerSelected && openDeveloperSettings != null) {
                openDeveloperSettings.run();
            } else if (showOnboarding || !listenerEnabled || !accessibilityEnabled) {
                if (enableAccess != null) enableAccess.run();
            } else if (clearSelected && clearHistory != null) {
                clearHistory.run();
                clearSelected = false;
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_PAGE_DOWN) { move(1); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_PAGE_UP) { move(-1); return true; }
        return false;
    }

    private void move(int delta) {
        if (showOnboarding) {
            developerSelected = delta > 0;
            invalidate();
            return;
        }
        if (delta > 0 && selected >= entries.size() - 1 && !clearSelected) {
            developerSelected = true;
            invalidate();
            return;
        }
        if (delta < 0 && developerSelected) {
            developerSelected = false;
            invalidate();
            return;
        }
        if (delta < 0 && !clearSelected && selected == 0) {
            clearSelected = true;
            invalidate();
            return;
        }
        if (delta > 0 && clearSelected) {
            clearSelected = false;
            selected = 0;
            invalidate();
            return;
        }
        if (entries.isEmpty()) return;
        clearSelected = false;
        developerSelected = false;
        selected = Math.max(0, Math.min(entries.size() - 1, selected + delta));
        invalidate();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            float value = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            move(value < 0 ? 1 : -1);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchStartY = event.getY();
            touchStartX = event.getX();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dy = event.getY() - touchStartY;
            if (Math.abs(dy) <= 28 && event.getY() > getHeight() * 0.88f) {
                if (openDeveloperSettings != null) openDeveloperSettings.run();
                return true;
            }
            if (showOnboarding) {
                if (Math.abs(dy) > 28) move(dy < 0 ? 1 : -1);
                else if (developerSelected && openDeveloperSettings != null) openDeveloperSettings.run();
                else if (enableAccess != null) enableAccess.run();
                return true;
            }
            if (Math.abs(dy) > 28) move(dy < 0 ? 1 : -1);
            else if (developerSelected && openDeveloperSettings != null) openDeveloperSettings.run();
            else if ((!listenerEnabled || !accessibilityEnabled) && enableAccess != null) {
                enableAccess.run();
            } else if (event.getY() < getHeight() * 0.14f &&
                    touchStartX > getWidth() * 0.55f && clearHistory != null) {
                clearHistory.run();
                clearSelected = false;
            }
            return true;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float margin = Math.max(18f, width * 0.035f);
        boolean portrait = height > width;

        if (showOnboarding) {
            drawOnboarding(canvas, margin, width, height);
            drawDeveloperControl(canvas, margin, width, height);
            return;
        }

        drawDeveloperControl(canvas, margin, width, height);

        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(Math.max(15f, Math.min(width, height) * 0.046f));
        paint.setColor(GREEN);
        canvas.drawText("HISTORY  " + entries.size(), margin, margin + paint.getTextSize(), paint);
        String clearLabel = "CLEAR";
        float clearWidth = paint.measureText(clearLabel);
        if (clearSelected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            canvas.drawRoundRect(new RectF(width - margin - clearWidth - 12f, margin * 0.45f,
                    width - margin + 8f, margin + paint.getTextSize() + 6f), 6f, 6f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        canvas.drawText(clearLabel, width - margin - clearWidth,
                margin + paint.getTextSize(), paint);

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(Math.max(12f, Math.min(width, height) * 0.034f));
        paint.setColor(listenerEnabled ? DIM_GREEN : Color.rgb(255, 185, 60));
        String status;
        if (!listenerEnabled) status = "STATUS: NOTIFICATION ACCESS OFF";
        else if (!accessibilityEnabled) status = "STATUS: ACCESSIBILITY OFF";
        else status = "LISTENING";
        if (portrait) {
            canvas.drawText(fit(status, width - margin * 2), margin,
                    margin * 1.55f + paint.getTextSize() * 2f, paint);
        } else {
            float statusWidth = paint.measureText(status);
            canvas.drawText(status, Math.max(margin, width - margin - statusWidth),
                    margin + paint.getTextSize(), paint);
        }

        float top = portrait ? margin * 2f + Math.min(width, height) * 0.105f :
                margin + Math.max(36f, height * 0.105f);
        paint.setColor(DIM_GREEN);
        paint.setStrokeWidth(2f);
        canvas.drawLine(margin, top, width - margin, top, paint);

        if (!listenerEnabled || !accessibilityEnabled) {
            drawSmallLine(canvas, "TAP TO ENABLE", margin, top + Math.min(width, height) * 0.09f, GREEN);
            return;
        }
        if (entries.isEmpty()) {
            drawSmallLine(canvas, "NO NOTIFICATIONS", margin,
                    top + Math.min(width, height) * 0.09f, GREEN);
            return;
        }

        float rowHeight = portrait ? Math.max(78f, Math.min(width, height) * 0.17f) :
                Math.max(72f, (height - top - margin) / 3f);
        int visibleRows = Math.max(1, (int) ((height * 0.86f - top) / rowHeight));
        int first = Math.max(0, Math.min(selected - visibleRows / 2, entries.size() - visibleRows));
        for (int row = 0; row < visibleRows && first + row < entries.size(); row++) {
            if (portrait) {
                drawEntryPortrait(canvas, entries.get(first + row), first + row, margin,
                        top + row * rowHeight, width - margin * 2, rowHeight);
            } else {
                drawEntry(canvas, entries.get(first + row), first + row, margin,
                        top + row * rowHeight, width - margin * 2, rowHeight);
            }
        }
    }

    private void drawOnboarding(Canvas canvas, float margin, float width, float height) {
        float unit = Math.min(width, height);
        float y = margin + unit * 0.06f;
        paint.setColor(GREEN);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(Math.max(17f, unit * 0.046f));
        canvas.drawText(fit("NOTIFICATION HISTORY", width - margin * 2), margin, y, paint);

        y += unit * 0.075f;
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(Math.max(13f, unit * 0.032f));
        paint.setColor(GREEN);
        y = drawWrapped(canvas,
                "To capture notifications and present their history, Notification History Capture must be enabled in Accessibility settings.",
                margin, y, width - margin * 2, paint.getTextSize() * 1.35f);

        y += unit * 0.035f;
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        y = drawWrapped(canvas,
                "Turn the option ON. Swipe down until ALLOW is highlighted and select it. Double-tap to go back and ensure Notification History Capture is now ON, then double-tap to go back to the app.",
                margin, y, width - margin * 2, paint.getTextSize() * 1.35f);

        y += unit * 0.035f;
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(DIM_GREEN);
        y = drawWrapped(canvas,
                "Notification information is stored only on the glasses and is not monitored or used for any other purpose.",
                margin, y, width - margin * 2, paint.getTextSize() * 1.35f);

        paint.setColor(GREEN);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(Math.max(15f, unit * 0.038f));
        String action = "TAP TO OPEN SETTINGS";
        float boxTop = Math.min(height * 0.80f - unit * 0.09f, y + unit * 0.06f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawRoundRect(new RectF(margin, boxTop, width - margin,
                boxTop + unit * 0.09f), 8f, 8f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(action, (width - paint.measureText(action)) / 2f,
                boxTop + unit * 0.059f, paint);
    }

    private void drawDeveloperControl(Canvas canvas, float margin, float width, float height) {
        float unit = Math.min(width, height);
        float top = height * 0.89f;
        paint.setColor(developerSelected ? GREEN : DIM_GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawRoundRect(new RectF(margin, top, width - margin,
                height - margin), 8f, 8f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(Math.max(13f, unit * 0.034f));
        String label = "OPEN WIRELESS DEBUGGING SETTINGS";
        canvas.drawText(fit(label, width - margin * 2 - 18f), margin + 9f,
                top + (height - margin - top + paint.getTextSize()) / 2f - 3f, paint);
    }

    private float drawWrapped(Canvas canvas, String text, float left, float y,
                              float maxWidth, float lineHeight) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && paint.measureText(candidate) > maxWidth) {
                canvas.drawText(line.toString(), left, y, paint);
                y += lineHeight;
                line.setLength(0);
                line.append(word);
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), left, y, paint);
            y += lineHeight;
        }
        return y;
    }

    private void drawEntryPortrait(Canvas canvas, NotificationEntry entry, int index, float left,
                                   float top, float width, float height) {
        boolean chosen = index == selected;
        if (chosen) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(12, 62, 35));
            canvas.drawRoundRect(new RectF(left, top + 5, left + width, top + height - 5), 8, 8, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(GREEN);
            canvas.drawRoundRect(new RectF(left, top + 5, left + width, top + height - 5), 8, 8, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float pad = Math.max(7f, getWidth() * 0.014f);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(Math.max(14f, getWidth() * 0.032f));
        paint.setColor(GREEN);
        String heading = timeFormat.format(new Date(entry.postedAt)) + "  Notification";
        float baseline = top + pad + paint.getTextSize();
        canvas.drawText(fit(heading, width - pad * 2), left + pad, baseline, paint);

        String title = senderAndContext(entry.title.isEmpty() ? entry.body : entry.title);
        paint.setTextSize(Math.max(13f, getWidth() * 0.029f));
        baseline += paint.getTextSize() + Math.max(2f, pad * 0.25f);
        canvas.drawText(fit(title, width - pad * 2), left + pad, baseline, paint);

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(Math.max(12f, getWidth() * 0.026f));
        paint.setColor(chosen ? GREEN : DIM_GREEN);
        String body = entry.title.isEmpty() ? "" : entry.body;
        baseline += paint.getTextSize() + Math.max(3f, pad * 0.35f);
        drawWrappedLimited(canvas, body, left + pad, baseline, width - pad * 2,
                paint.getTextSize() * 1.18f, 2);
    }

    private void drawEntry(Canvas canvas, NotificationEntry entry, int index, float left, float top,
                           float width, float height) {
        boolean chosen = index == selected;
        if (chosen) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(12, 62, 35));
            canvas.drawRoundRect(new RectF(left, top + 5, left + width, top + height - 5), 8, 8, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(GREEN);
            canvas.drawRoundRect(new RectF(left, top + 5, left + width, top + height - 5), 8, 8, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float pad = 12f;
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        paint.setTextSize(Math.max(15f, getHeight() * 0.045f));
        paint.setColor(GREEN);
        String heading = timeFormat.format(new Date(entry.postedAt)) + "  " + entry.appName;
        canvas.drawText(fit(heading, width * 0.42f), left + pad, top + pad + paint.getTextSize(), paint);

        String title = entry.title.isEmpty() ? entry.body : entry.title;
        float titleX = left + width * 0.43f;
        canvas.drawText(fit(title, width * 0.54f), titleX, top + pad + paint.getTextSize(), paint);

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(Math.max(13f, getHeight() * 0.038f));
        paint.setColor(chosen ? GREEN : DIM_GREEN);
        String body = entry.title.isEmpty() ? "" : entry.body;
        canvas.drawText(fit(body, width - pad * 2), left + pad,
                top + height - pad - Math.max(2f, getHeight() * 0.012f), paint);
    }

    private String fit(String value, float maxWidth) {
        if (value == null) return "";
        if (paint.measureText(value) <= maxWidth) return value;
        String ellipsis = "…";
        int length = paint.breakText(value, true, Math.max(0, maxWidth - paint.measureText(ellipsis)), null);
        return value.substring(0, Math.max(0, length)) + ellipsis;
    }

    private void drawWrappedLimited(Canvas canvas, String value, float left, float baseline,
                                    float maxWidth, float lineHeight, int maxLines) {
        if (value == null || value.isEmpty() || maxLines <= 0) return;
        String remaining = value.trim();
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            int count = paint.breakText(remaining, true, maxWidth, null);
            if (count <= 0) return;
            if (count < remaining.length()) {
                int space = remaining.lastIndexOf(' ', Math.max(0, count - 1));
                if (space > count / 2) count = space;
            }
            String shown = remaining.substring(0, count).trim();
            remaining = remaining.substring(count).trim();
            if (line == maxLines - 1 && !remaining.isEmpty()) shown = fit(shown + " " + remaining, maxWidth);
            canvas.drawText(shown, left, baseline + line * lineHeight, paint);
        }
    }

    private String senderAndContext(String value) {
        if (value == null) return "";
        String[] parts = value.split("\\s*[|]\\s*");
        if (parts.length < 2) return value;
        String sender = parts[parts.length - 1].trim();
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].trim().isEmpty()) continue;
            if (context.length() > 0) context.append(" · ");
            context.append(parts[i].trim());
        }
        return context.length() == 0 ? sender : sender + " · " + context;
    }

    private void drawCentered(Canvas canvas, String text, float y, float size, int color) {
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(Math.max(13f, size));
        paint.setColor(color);
        canvas.drawText(text, (getWidth() - paint.measureText(text)) / 2f, y, paint);
    }

    private void drawSmallLine(Canvas canvas, String text, float x, float y, int color) {
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(Math.max(12f, Math.min(getWidth(), getHeight()) * 0.034f));
        paint.setColor(color);
        canvas.drawText(fit(text, getWidth() - x * 2), x, y, paint);
    }
}
