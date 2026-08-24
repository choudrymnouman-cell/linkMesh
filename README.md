# LinkMesh Offline Chat

A local-first Flutter messenger recreated from the supplied native Android APK. LinkMesh is designed for nearby communication when normal internet service is unavailable. Devices discover one another on the same Wi-Fi/hotspot and exchange traffic directly over the LAN.

## Features

- Persistent local profile and stable node ID
- UDP nearby-device discovery with online/offline expiry
- Direct TCP text messaging with pending/delivered/failed status
- Persistent chat history across restarts
- Favorites and local blocking
- Broadcast group creation and group messaging
- Community broadcasts
- SOS emergency beacon with safety disclaimer
- Dark mode
- Call-attempt history (voice/video media itself is not implemented)
- Mesh diagnostics screen
- Local data reset
- Android Flutter scaffold and Gradle configuration

## Network

- Discovery/broadcasts: UDP 40444
- Direct messages: TCP 40445
- Current transport scope: devices on the same reachable LAN/hotspot

This is **not yet a true multi-hop Bluetooth/Wi-Fi Direct mesh**. A phone does not relay another phone's packet to a third phone. Voice/video media transport and file transfer are also intentionally not claimed as complete.

## Run and verify

```bash
flutter pub get
flutter analyze
flutter test
flutter run
```

For an end-to-end test, install on two Android phones, connect both to the same Wi-Fi or one phone's hotspot, open LinkMesh, create profiles, and confirm they appear under Nearby. Send direct messages in both directions, restart each app to confirm history persists, then test community/group/SOS broadcasts.

## APK CI artifact

Every successful CI build now uploads `app-debug.apk` as the `linkmesh-debug-apk` GitHub Actions artifact.

## Architecture note

The supplied APK was a native Android application, not a Flutter APK. This repository is a clean Flutter recreation based on APK behavior and resources, not recovered original source code.
