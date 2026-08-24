# LinkMesh Offline Chat

LinkMesh is a local-first Flutter messenger for nearby Android phones. It works without a cloud account and can communicate through shared Wi-Fi, a phone hotspot, or a BLE-assisted Wi-Fi Direct group.

## Completed features

- AES-256-GCM encrypted mesh packets and secure QR key pairing
- UDP discovery, direct TCP acknowledgements, retries, offline queueing, and SQLite history
- Four-hop encrypted routing with destination IDs, duplicate suppression, relay paths, and loop prevention
- Direct and private-group messages, replies, reactions, search, favorites, and blocking
- Owner/admin group roles, invitations, removals, and member-only delivery
- Encrypted file/media transfer up to 5 MB and recorded voice notes
- Real peer-to-peer WebRTC voice/video calls with accept, reject, mute, camera, switch-camera, and hang-up controls
- Community broadcasts and location-aware SOS relayed across the mesh
- High-priority Android message, file, group, call, and SOS notifications
- Android foreground mesh listening while the UI is minimized, with queued packet replay on resume
- Bluetooth LE discovery and Wi-Fi Direct host/client setup for router-free communication
- Encrypted backup/restore, diagnostics, theme selection, and local-data reset

## Network architecture

- Discovery and routed broadcasts: UDP `40444`
- Direct messages and transfers: TCP `40445`
- Wi-Fi Direct: BLE-assisted discovery followed by high-speed Wi-Fi P2P networking
- Routing limit: four forwarding hops per encrypted route envelope
- Calls: WebRTC DTLS-SRTP media between directly reachable peers; LinkMesh carries encrypted signaling

All phones in a private mesh must share the same QR-paired key. Multi-hop forwarding extends application packets through LinkMesh relay phones, but every relay needs a reachable path to the next hop. Android power management, radio hardware, and manufacturer restrictions can affect background availability and Wi-Fi Direct behavior.

## Run and verify

```bash
flutter pub get
flutter analyze
flutter test
flutter run
```

For a basic test, install the CI APK on two Android phones, pair them with the same LinkMesh QR key, and connect through Wi-Fi/hotspot or the **Bluetooth & Wi-Fi Direct** screen. For multi-hop testing, use at least three phones and keep the relay phone connected to both reachable network segments.

## APK artifact

Every successful CI build now uploads `app-debug.apk` as the `linkmesh-debug-apk` GitHub Actions artifact.

## Architecture note

The supplied reference APK was native Android. This repository is a clean Flutter recreation based on its behavior and resources, not recovered original source code.
