# Notification History release signing

The encrypted backup in this folder contains the permanent Notification History release keystore and
Gradle credentials. GitHub Actions decrypts it using the repository secret
`NOTIFICATION_SIGNING_BACKUP_PASSWORD`.

Never commit the recovery password or unencrypted signing files. This identity begins with the next
release; because earlier diagnostic builds used changing debug certificates, users must uninstall
the old diagnostic APK once before installing the first permanently signed release.

The encrypted repository backup and Actions secret are subject to clean rebuild verification.
