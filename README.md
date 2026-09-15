# Rokid Notification History

A small, open-source Android application for Rokid AI glasses that captures mirrored notification popups and keeps a local, scrollable history on the glasses.

![Simulated Notification History screen](docs/example-screen.png)

> The image above is a simulated example. Names and messages are fictional.

## Features

- Keeps notifications available when a new notification replaces a popup before you have finished reading it
- Often preserves more notification text than fits in the original glasses popup—open History to read the fuller message
- Portrait HUD interface designed for the Rokid glasses display
- Captures notification text exposed by Rokid's Accessibility UI
- Supports Rokid notification countdown settings from 5 to 30 seconds
- Deduplicates repeated countdown updates into one history entry
- Filters ordinary notifications from sideloaded apps and stale Sprite Launcher content
- Displays time, sender/group, and up to two lines of message text
- Scrollable history of up to 200 notifications
- Clear-history control
- **DEBUG** beside CLEAR opens Android device information to enable Developer options, then Developer options on the next visit
- English and Hebrew/RTL notification content
- Fully local storage; the optional setup uses loopback ADB only and does not upload notifications
- Optional accessibility recovery after reboot (requires a one-time ADB grant)

## Privacy

Notification text is stored only in the application's private SQLite database on the glasses and is never transmitted, analyzed, or used for any other purpose. The app declares Internet permission solely because Android treats its one-time ADB connection to `127.0.0.1` as network access.

Accessibility access is required because consumer Rokid firmware does not expose mirrored iPhone notifications through Android's standard notification listener API. Android requires the user to enable this service manually.

## One-time setup

> **The Android Settings screens are not designed for the glasses display.** Some labels, buttons, or the selection highlight may be clipped, oddly positioned, or difficult to read. Navigate slowly with swipes or the glasses controls. The setup is awkward, but it is possible and only needs to be completed once.

### 1. Install and enable notification capture

1. Build the APK or download it from the repository's Releases page.
2. Sideload it onto the Rokid glasses.
3. Launch **Notification History**.
4. Tap the instruction screen. Android's **Accessibility** settings will open.
5. Carefully navigate the poorly formatted settings screen until **Notification History Capture** is selected, then turn it on.
6. Android displays a confirmation screen. Swipe down until **ALLOW** is highlighted, then select it.
7. Use the Back gesture/control once and verify that **Notification History Capture** is shown as on.
8. Go back again to return to the app. The screen should now say **LISTENING**.

The instruction screen remains visible until the app detects that its Accessibility service is enabled.

### 2. Enable automatic recovery after shutdown or restart

Android may disable the Accessibility service after a restart. The following one-time, on-glasses procedure gives the app permission to restore its own service automatically. It does **not** require a PC, USB cable, or typing an ADB command.

1. In Notification History, select **DEBUG**, then **SELF-PAIR**.
2. If Android opens **Device information**, find **Build number** and select it seven times to enable Developer options. Return to Notification History, select **DEBUG**, then **SELF-PAIR** again.
3. Android opens **Developer options**. Find **Wireless debugging** and turn it on. Confirm Android's prompt if one appears.
4. Open **Wireless debugging**, then select **Pair device with pairing code**.
5. Leave the pairing-code dialog open. Do not type or remember the code: Notification History reads the dialog through its enabled Accessibility service and pairs locally with the glasses.
6. Wait for pairing to complete, then return to Notification History.
7. Open **DEBUG** and confirm it reports **Recovery ready; grant applied** and **granted**.
8. Restart the glasses once as a test. Open Notification History; it should already show **LISTENING** without repeating setup.

If a control is hard to find, remember that Android's Developer and Accessibility screens are wider/taller than the glasses viewport. Swipe through the entire screen slowly, watch for the selection highlight, and use Back to recover if you enter the wrong item.

## Controls

- Swipe up/down or use D-pad/volume controls to browse history.
- Swipe upward from the first notification to highlight **CLEAR**, then select it.
- **CLEAR** may also be tapped directly.
- Tap **DEBUG** beside CLEAR (also available on the setup screen). If Developer options are off, select **Build number** seven times in Android device information, then return and tap **DEBUG** again. Under Developer options, look for **Wireless debugging**. Availability depends on the glasses firmware.
- **DEBUG** opens the recovery control and an Android settings shortcut.

## Reboot recovery details

Enable **Notification History Capture** in Android Accessibility settings first. Open **DEBUG** and select **SELF-PAIR**. In Android Developer options, turn on **Wireless debugging**, then open **Pair device with pairing code**. Leave the pairing dialog open. The app's Accessibility service reads the dialog and pairs its embedded ADB client to `127.0.0.1`; it grants `WRITE_SECURE_SETTINGS` and enables recovery. Return to the app and open DEBUG to check for **Recovery ready; grant applied** and **granted**. No PC, USB cable, or command entry is needed. The device must have Android 11 or later and expose the Wireless debugging pairing dialog to Accessibility.

If an external ADB connection is available, the equivalent manual grant is:

```bash
adb shell pm grant com.shabp.rokid.notificationhistory android.permission.WRITE_SECURE_SETTINGS
```

The app checks and repairs its Accessibility registration after `BOOT_COMPLETED`, package update, and app launch. It preserves other enabled Accessibility services. Select **TURN OFF** in DEBUG to stop automatic repair; to remove the elevated grant too, run (when ADB is available):

```bash
adb shell pm revoke com.shabp.rokid.notificationhistory android.permission.WRITE_SECURE_SETTINGS
```

Self-pairing uses Android's temporary Wireless debugging ports and does not enable persistent network ADB. The private pairing key stays in the app's internal storage. Internet permission is used only for the loopback ADB connection; notification content remains local. Recovery was tested successfully on Rokid AI glasses firmware `1.25.012-20260901-150201`: after shutdown and restart, the app opened directly in **LISTENING** mode without repeating setup. Other firmware versions may behave differently; opening the app retries recovery if boot delivery was suppressed.

## Occasional missing popup

Very brief popups can occasionally be absent from History even though they appeared on the glasses and capture remains in **LISTENING** mode. This is most likely when notifications arrive close together. Rokid exposes a popup through several Accessibility updates; if the popup is replaced while an update contains only partial text and no recognizable notification marker or countdown, the current collector rejects that partial event and may never receive a later complete one. Notifications captured immediately before and after such a gap indicate this event-level timing condition, not that the Accessibility service stopped.

## Build

Requirements:

- Android SDK 36
- JDK 17 or newer
- Gradle 8.x

From the project root:

```bash
gradle assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

No Rokid SDK binary is required: the optional Rokid system-service experiment uses Android Binder primitives directly. Notification capture on tested consumer firmware uses the Accessibility service.

## Technical overview

- `NotificationAccessibilityService` recognizes Rokid/Sprite notification popups and removes countdown updates.
- `NotificationCollectorService` provides standard Android notification-listener support where available.
- `HistoryStore` keeps up to 200 entries in a private SQLite database.
- `NotificationHistoryView` draws the compact glasses-oriented interface without external UI dependencies.

## Device compatibility

Developed and tested on Rokid AI glasses. Rokid firmware behavior may vary between consumer, enterprise, and future firmware versions.

## License

MIT License. See [LICENSE](LICENSE).
