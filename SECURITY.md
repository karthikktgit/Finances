# Security

Ledgerline keeps the ledger in the app's private storage. Backup rules exclude the database and DataStore. `allowBackup` is false. The network security config is present, and the app does not request the internet permission because the current provider does not call a network API.

## Secrets

No banking password is collected or stored. The development fixture does not use credentials. The PIN is not stored. `PinStore` creates an HMAC-SHA256 key in Android Keystore and stores only the MAC of the PIN in DataStore. Unlock compares a new MAC with the stored MAC.

Biometrics use the platform `BiometricPrompt` as another way to unlock. The PIN remains the fallback. Leaving the app starts a timer. Coming back after the timeout locks the screen again. The default timeout is one minute.

Screenshot blocking is optional and uses `FLAG_SECURE`. It is off until you turn it on in Settings.

## Logs and reminders

Maintenance failures log the exception class name only. Notification text is not logged. Reminder notifications are off until you enable a type and, on Android 13 and newer, grant notification permission.

Release builds minify with R8. The rules keep serialization and Room classes that those tools need.

Database encryption with SQLCipher is not enabled. The file is private to the app and excluded from backup. Add encryption in a later migration if the device-loss threat requires it.
