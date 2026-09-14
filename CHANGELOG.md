# Changelog

## 1.9.1 — 2026-09-14

- Added an on-glasses self-pairing flow that needs no PC or USB development cable.
- Added a one-time, explicit secure-settings grant for accessibility recovery.
- Restores the notification-capture Accessibility service after shutdown/restart and package updates.
- Preserves other enabled Accessibility services.
- Uses only temporary Wireless debugging ports and localhost; persistent network ADB is not enabled.
- Verified on Rokid AI glasses firmware `1.25.012-20260901-150201` through shutdown, restart, and successful **LISTENING** state.

## 1.8.3

- Added the developer-settings shortcut used to reach Wireless debugging.
- Moved the DEBUG control into the header.

## 1.8.1

- Initial public notification-history implementation using Rokid Accessibility events.
