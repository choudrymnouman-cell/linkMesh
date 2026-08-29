import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:path_provider/path_provider.dart';
import 'package:geolocator/geolocator.dart';
import 'package:crypto/crypto.dart';
import 'package:image/image.dart' as image_lib;
import 'package:just_audio/just_audio.dart';
import 'models/models.dart';
import 'services/local_mesh_service.dart';
import 'services/local_store.dart';
import 'services/secure_mesh_codec.dart';
import 'services/notification_service.dart';
import 'services/call_service.dart';
import 'services/background_mesh_service.dart';
import 'services/p2p_transport_service.dart';
import 'services/qr_pairing.dart';

class AppState extends ChangeNotifier {
  AppState() {
    callService = CallService(_sendCallSignal);
    callService.addListener(_handleCallState);
  }
  final LocalMeshService mesh = LocalMeshService();
  final NotificationService notifications = NotificationService();
  final BackgroundMeshService backgroundMesh = BackgroundMeshService();
  final P2pTransportService p2p = P2pTransportService();
  late final CallService callService;
  final AudioPlayer _alertPlayer = AudioPlayer();
  StreamSubscription<MeshPacket>? _sub;
  Timer? _peerSweep;
  SharedPreferences? _prefs;
  LocalStore? _store;
  Future<void> _persistQueue = Future<void>.value();
  String deviceId = '';
  String username = 'Mesh User';
  String meshCode = '';
  String profilePhotoPath = '';
  String profilePhotoHash = '';
  String pendingPairDeviceId = '';
  String pendingPairDeviceName = '';
  bool onboarded = false;
  bool sosActive = false;
  bool darkMode = false;
  bool backgroundNotifications = true;
  bool automaticDirectConnect = true;
  bool networkRunning = false;
  bool initialized = false;
  String? startupWarning;
  String? networkError;
  String? callError;
  final Map<String, Completer<void>> _deliveryAcks = {};
  final Set<String> _retryingPeers = {};
  final Set<String> _avatarRequests = {};
  final Map<String, _IncomingAttachment> _incomingAttachments = {};
  final List<MeshPeer> peers = [];
  final List<ChatMessage> messages = [];
  final List<MeshGroup> groups = [];
  final List<CommunityPost> posts = [];
  final List<CallRecord> calls = [];
  final List<String> feedbackMessages = [];

  Future<void> initialize() async {
    final warnings = <String>[];
    try {
      try {
        _prefs = await SharedPreferences.getInstance().timeout(const Duration(seconds: 5));
      } catch (error) {
        warnings.add('Settings could not be loaded');
        debugPrint('LinkMesh settings initialization failed: $error');
      }
      try {
        _store = await LocalStore.open().timeout(const Duration(seconds: 8));
      } catch (error) {
        warnings.add('Local history is temporarily unavailable');
        debugPrint('LinkMesh database initialization failed: $error');
      }

      deviceId = _prefs?.getString('deviceId') ?? const Uuid().v4();
      username = _prefs?.getString('username') ?? 'Mesh User';
      meshCode = _prefs?.getString('meshCode') ?? '';
      profilePhotoPath = _prefs?.getString('profilePhotoPath') ?? '';
      profilePhotoHash = _prefs?.getString('profilePhotoHash') ?? '';
      pendingPairDeviceId = _prefs?.getString('pendingPairDeviceId') ?? '';
      pendingPairDeviceName = _prefs?.getString('pendingPairDeviceName') ?? '';
      onboarded = _prefs?.getBool('onboarded') ?? false;
      darkMode = _prefs?.getBool('darkMode') ?? false;
      backgroundNotifications = _prefs?.getBool('backgroundNotifications') ?? true;
      automaticDirectConnect = _prefs?.getBool('automaticDirectConnect') ?? true;
      feedbackMessages.addAll(_prefs?.getStringList('feedbackMessages') ?? const []);
      if (onboarded && !validMeshSecret(meshCode)) onboarded = false;
      final migrated = _prefs?.getBool('sqliteMigrated') ?? false;
      await Future.wait([
        _loadList('peers', (j) => MeshPeer.fromJson(j), peers, migrated),
        _loadList('messages', (j) => ChatMessage.fromJson(j), messages, migrated),
        _loadList('groups', (j) => MeshGroup.fromJson(j), groups, migrated),
        _loadList('posts', (j) => CommunityPost.fromJson(j), posts, migrated),
        _loadList('calls', (j) => CallRecord.fromJson(j), calls, migrated),
      ]).timeout(const Duration(seconds: 8));
      for (final group in groups.where((g) => g.ownerId.isEmpty && g.members.contains(deviceId))) { group.ownerId = deviceId; if (!group.adminIds.contains(deviceId)) group.adminIds.add(deviceId); }
      groups.removeWhere((group) => group.id == 'emergency' || group.name == 'Emergency Mesh Group');
      if (posts.isEmpty) posts.add(CommunityPost(id: 'welcome', author: 'LinkMesh', text: 'Local mesh ready. Nearby devices can discover this phone while the app is open.', createdAt: DateTime.now()));
      await _prefs?.setString('deviceId', deviceId).timeout(const Duration(seconds: 3));
      if (!migrated && _store != null) {
        await _persist().timeout(const Duration(seconds: 5));
        await _prefs?.setBool('sqliteMigrated', true).timeout(const Duration(seconds: 3));
      }
    } catch (error, stackTrace) {
      warnings.add('Some saved data could not be restored');
      debugPrint('LinkMesh core startup failed: $error\n$stackTrace');
    } finally {
      startupWarning = warnings.isEmpty ? null : warnings.join('. ');
      initialized = true;
      notifyListeners();
    }

    // Native integrations are useful but must never block the first screen.
    unawaited(_initializeOptionalServices());
    if (onboarded) unawaited(startNetwork());
  }

  Future<void> _initializeOptionalServices() async {
    try { await notifications.initialize().timeout(const Duration(seconds: 4)); } catch (error) { debugPrint('Notifications unavailable: $error'); }
    try { await callService.initialize().timeout(const Duration(seconds: 4)); } catch (error) { debugPrint('Call renderer unavailable: $error'); }
    try { backgroundMesh.initialize(); await backgroundMesh.stop().timeout(const Duration(seconds: 4)); } catch (error) { debugPrint('Background mesh unavailable: $error'); }
    try { await p2p.initialize().timeout(const Duration(seconds: 5)); } catch (error) { debugPrint('Wi-Fi Direct unavailable: $error'); }
  }

  Future<void> _loadList<T>(String key, T Function(Map<String, dynamic>) parse, List<T> target, bool migrated) async {
    if (migrated) {
      final store = _store;
      if (store != null) target.addAll((await store.readCollection(key)).map(parse));
      return;
    }
    final raw = _prefs?.getString(key); if (raw == null) return;
    try { target.addAll((jsonDecode(raw) as List).map((e) => parse(Map<String, dynamic>.from(e as Map)))); } catch (_) { /* Ignore malformed legacy data during one-time migration. */ }
  }
  Future<void> _persist() {
    _persistQueue = _persistQueue.then((_) => _persistNow(), onError: (_) => _persistNow());
    return _persistQueue;
  }
  Future<void> _persistNow() async {
    final p = _prefs;
    if (p != null) { await p.setString('username', username); await p.setString('meshCode', meshCode); await p.setBool('onboarded', onboarded); await p.setBool('darkMode', darkMode); await p.setBool('backgroundNotifications', backgroundNotifications); await p.setBool('automaticDirectConnect', automaticDirectConnect); await p.setStringList('feedbackMessages', feedbackMessages.take(100).toList()); await p.setString('profilePhotoPath', profilePhotoPath); await p.setString('profilePhotoHash', profilePhotoHash); await p.setString('pendingPairDeviceId', pendingPairDeviceId); await p.setString('pendingPairDeviceName', pendingPairDeviceName); }
    if (messages.length > 2000) messages.removeRange(0, messages.length - 2000);
    final store = _store;
    if (store == null) return;
    await store.replaceCollection('peers', peers.map((e) => e.toJson()));
    await store.replaceCollection('messages', messages.map((e) => e.toJson()));
    await store.replaceCollection('groups', groups.map((e) => e.toJson()));
    await store.replaceCollection('posts', posts.take(200).map((e) => e.toJson()));
    await store.replaceCollection('calls', calls.take(100).map((e) => e.toJson()));
  }

  bool validMeshSecret(String value) => RegExp(r'^\d{6}$').hasMatch(value.trim()) || RegExp(r'^[a-fA-F0-9]{64}$').hasMatch(value.trim());
  String generateStrongMeshSecret() {
    final random = Random.secure();
    return List<int>.generate(32, (_) => random.nextInt(256)).map((value) => value.toRadixString(16).padLeft(2, '0')).join();
  }
  Future<void> setProfile(String name, String code) async { username = name.trim().isEmpty ? 'Mesh User' : name.trim(); meshCode = code.trim(); if (!validMeshSecret(meshCode)) return; onboarded = true; await _persist(); notifyListeners(); await startNetwork(); }
  Future<void> updateProfile(String name) async { username = name.trim().isEmpty ? username : name.trim(); await _persist(); notifyListeners(); if (networkRunning) { await restartNetwork(); } }
  Future<void> updateMeshCode(String code) async { if (!validMeshSecret(code)) return; meshCode = code.trim(); await _persist(); notifyListeners(); await restartNetwork(); }

  Future<bool> pickProfilePhoto() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.image, withData: true);
    final picked = result?.files.single;
    var bytes = picked?.bytes;
    if (bytes == null && picked?.path != null) bytes = await File(picked!.path!).readAsBytes();
    if (bytes == null || bytes.isEmpty) return false;
    final decoded = image_lib.decodeImage(bytes);
    if (decoded == null) return false;
    final resized = decoded.width > 256 || decoded.height > 256
        ? image_lib.copyResize(decoded, width: decoded.width >= decoded.height ? 256 : null, height: decoded.height > decoded.width ? 256 : null)
        : decoded;
    final compressed = Uint8List.fromList(image_lib.encodeJpg(resized, quality: 72));
    if (compressed.length > 60 * 1024) return false;
    final directory = await getApplicationDocumentsDirectory();
    final file = File('${directory.path}/linkmesh_profile.jpg');
    await file.writeAsBytes(compressed, flush: true);
    profilePhotoPath = file.path;
    profilePhotoHash = sha256.convert(compressed).toString();
    mesh.setPresenceData({'avatarHash': profilePhotoHash});
    await _persist();
    notifyListeners();
    return true;
  }

  Future<void> completeQrPairing(LinkMeshQrData pairing) async {
    meshCode = pairing.secret;
    pendingPairDeviceId = pairing.deviceId;
    pendingPairDeviceName = pairing.deviceName;
    if (pairing.hasDevice && !peers.any((peer) => peer.id == pairing.deviceId)) {
      peers.add(MeshPeer(id: pairing.deviceId, name: pairing.deviceName.isEmpty ? 'Paired device' : pairing.deviceName, host: '', online: false));
    }
    await _persist();
    notifyListeners();
    if (onboarded) await restartNetwork();
  }

  Future<void> startNetwork() async {
    if (!onboarded || networkRunning) return;
    networkError = null;
    try {
      final nearby = await Permission.nearbyWifiDevices.request();
      if (!nearby.isGranted && !nearby.isLimited) {
        final location = await Permission.locationWhenInUse.request();
        if (!location.isGranted && !location.isLimited) {
          throw StateError('Nearby-device permission is required for local discovery. Enable it in Android Settings.');
        }
      }
      _sub ??= mesh.packets.listen(_onPacket);
      if (!validMeshSecret(meshCode)) throw StateError('A valid private mesh key is required.');
      mesh.setPresenceData({'avatarHash': profilePhotoHash});
      await mesh.start(id: deviceId, name: username, meshCode: meshCode);
      networkRunning = true;
      _peerSweep ??= Timer.periodic(const Duration(seconds: 2), (_) => _expirePeers());
      if (pendingPairDeviceId.isNotEmpty) unawaited(mesh.sendRoutedPacket(pendingPairDeviceId, 'pair_request', {'targetId': pendingPairDeviceId}));
      if (automaticDirectConnect && Platform.isAndroid) {
        Future<void>.delayed(const Duration(seconds: 5), () async {
          if (networkRunning && peers.every((peer) => !peer.online)) {
            await p2p.startAutomatic(deviceId);
            if (p2p.connected || p2p.hosting) await restartNetwork();
          }
        });
      }
    } catch (e) { networkRunning = false; networkError = e.toString(); }
    notifyListeners();
  }
  Future<void> restartNetwork() async { await mesh.stop(); networkRunning = false; notifyListeners(); await startNetwork(); }
  Future<void> stopNetwork() async { await mesh.stop(); networkRunning = false; for (final p in peers) { p.online = false; } await _persist(); notifyListeners(); }
  Future<void> enterBackground() async {
    if (!onboarded || callService.active) return;
    if (networkRunning) await stopNetwork();
    await backgroundMesh.start(id: deviceId, name: username, meshCode: meshCode, avatarHash: profilePhotoHash, alertsEnabled: backgroundNotifications);
  }
  Future<void> resumeFromBackground() async { await backgroundMesh.stop(); for (final packet in await backgroundMesh.drainPackets()) { _onPacket(packet); } if (onboarded && !networkRunning) await startNetwork(); }
  Future<void> createP2pGroup() async { await p2p.createGroup(); await restartNetwork(); }
  Future<void> connectP2pHost(dynamic device) async { await p2p.connect(device); await restartNetwork(); }
  Future<void> disconnectP2p() async { await p2p.disconnect(); await restartNetwork(); }

  void _expirePeers() {
    final now = DateTime.now(); bool changed = false;
    for (final p in peers) { final online = p.lastSeen != null && now.difference(p.lastSeen!).inSeconds < 5; if (p.online != online) { p.online = online; changed = true; } }
    if (changed) { _persist(); notifyListeners(); }
  }

  void _onPacket(MeshPacket packet) {
    final host = packet.payload['host']?.toString() ?? '';
    var peer = peers.where((p) => p.id == packet.senderId).firstOrNull;
    if (peer == null) { peer = MeshPeer(id: packet.senderId, name: packet.senderName, host: host, lastSeen: DateTime.now()); peers.add(peer); }
    else { peer.name = packet.senderName; if (host.isNotEmpty) peer.host = host; peer.online = true; peer.lastSeen = DateTime.now(); }
    if (peer.blocked) { _persist(); notifyListeners(); return; }
    if (packet.type == 'presence') {
      unawaited(_flushQueuedMessages(peer));
      final remoteHash = packet.payload['avatarHash']?.toString() ?? '';
      if (remoteHash.isNotEmpty && remoteHash != peer.avatarHash && host.isNotEmpty && _avatarRequests.add(peer.id)) {
        unawaited(mesh.sendToHost(host, 'profile_request', {'avatarHash': remoteHash}));
        Future<void>.delayed(const Duration(seconds: 10), () => _avatarRequests.remove(peer!.id));
      }
      if (pendingPairDeviceId == peer.id) {
        pendingPairDeviceId = '';
        pendingPairDeviceName = '';
        unawaited(mesh.sendToHost(host, 'pair_request', {'targetId': peer.id}));
      }
    }
    final id = packet.payload['id']?.toString() ?? '${DateTime.now().microsecondsSinceEpoch}';
    if (packet.type == 'pair_request') {
      final target = packet.payload['targetId']?.toString() ?? '';
      if (host.isNotEmpty && (target.isEmpty || target == deviceId)) {
        unawaited(mesh.sendToHost(host, 'pair_accept', {'avatarHash': profilePhotoHash}));
      }
    } else if (packet.type == 'pair_accept') {
      pendingPairDeviceId = '';
      pendingPairDeviceName = '';
      unawaited(notifications.showMessage('LinkMesh connected', '${peer.name} is ready to chat'));
    } else if (packet.type == 'profile_request') {
      if (host.isNotEmpty && profilePhotoPath.isNotEmpty) unawaited(_sendProfilePhoto(host));
    } else if (packet.type == 'profile_data') {
      unawaited(_savePeerPhoto(peer, packet.payload));
    } else if (packet.type == 'message') {
      if (!messages.any((m) => m.id == id)) {
        messages.add(ChatMessage(id: id, peerId: packet.senderId, sender: packet.senderName, text: packet.payload['text']?.toString() ?? '', sentAt: DateTime.now(), mine: false, replyToId: packet.payload['replyToId']?.toString()));
        unawaited(notifications.showMessage(packet.senderName, packet.payload['text']?.toString() ?? 'New encrypted message'));
      }
      // Always acknowledge retries; the message itself remains de-duplicated.
      if (packet.payload['relayed'] == true) {
        unawaited(mesh.sendRoutedPacket(packet.senderId, 'ack', {'id': id}));
      } else if (host.isNotEmpty) {
        unawaited(mesh.sendToHost(host, 'ack', {'id': id}));
      }
    } else if (packet.type == 'ack') {
      _deliveryAcks.remove(id)?.complete();
    } else if (packet.type == 'reaction') {
      final target = messages.where((m) => m.id == id).firstOrNull;
      final emoji = packet.payload['emoji']?.toString() ?? '';
      if (target != null) {
        if (emoji.isEmpty) {
          target.reactions.remove(packet.senderId);
        } else {
          target.reactions[packet.senderId] = emoji;
        }
      }
    } else if (packet.type == 'file_start') {
      final total = int.tryParse('${packet.payload['totalChunks']}') ?? 0;
      final size = int.tryParse('${packet.payload['size']}') ?? 0;
      if (total > 0 && total <= 256 && size > 0 && size <= 5 * 1024 * 1024) {
        _incomingAttachments[id] = _IncomingAttachment(name: _safeFileName(packet.payload['name']?.toString() ?? 'attachment.bin'), mime: packet.payload['mime']?.toString() ?? 'application/octet-stream', size: size, totalChunks: total);
      }
      return;
    } else if (packet.type == 'file_chunk') {
      final incoming = _incomingAttachments[id];
      final index = int.tryParse('${packet.payload['index']}') ?? -1;
      if (incoming != null && index >= 0 && index < incoming.totalChunks && !incoming.chunks.containsKey(index)) {
        try { incoming.chunks[index] = base64Decode(packet.payload['data']?.toString() ?? ''); } on FormatException { _incomingAttachments.remove(id); }
      }
      return;
    } else if (packet.type == 'file_end') {
      final incoming = _incomingAttachments.remove(id);
      if (incoming != null && incoming.chunks.length == incoming.totalChunks) unawaited(_finishIncomingAttachment(packet, peer, host, id, incoming));
      return;
    } else if (packet.type == 'group_message' && !messages.any((m) => m.id == id)) {
      final groupId = packet.payload['groupId']?.toString();
      final group = groups.where((g) => g.id == groupId).firstOrNull;
      if (group != null && (!group.isPrivate || group.members.contains(deviceId))) {
        messages.add(ChatMessage(id: id, peerId: packet.senderId, sender: packet.senderName, text: packet.payload['text']?.toString() ?? '', sentAt: DateTime.now(), mine: false, groupId: groupId));
        unawaited(notifications.showMessage('${group.name} • ${packet.senderName}', packet.payload['text']?.toString() ?? 'New group message'));
      }
    } else if (packet.type == 'group_invite') {
      final group = _groupFromPacket(packet);
      if (group != null && group.members.contains(deviceId)) { groups.removeWhere((g) => g.id == group.id); groups.add(group); }
    } else if (packet.type == 'group_update') {
      final current = groups.where((g) => g.id == packet.payload['groupId']?.toString()).firstOrNull;
      final updated = _groupFromPacket(packet);
      if (current != null && updated != null && (current.ownerId == packet.senderId || current.adminIds.contains(packet.senderId))) { groups.remove(current); if (updated.members.contains(deviceId)) groups.add(updated); }
    } else if (packet.type == 'sos') {
      final latitude = double.tryParse('${packet.payload['latitude']}');
      final longitude = double.tryParse('${packet.payload['longitude']}');
      final detail = 'SOS: ${packet.payload['text'] ?? 'Emergency assistance requested.'}${latitude == null || longitude == null ? '' : ' Location: $latitude, $longitude'}';
      posts.insert(0, CommunityPost(id: id, author: packet.senderName, text: detail, createdAt: DateTime.now(), emergency: true, latitude: latitude, longitude: longitude));
      unawaited(notifications.showSos(packet.senderName, detail));
      unawaited(_playSiren());
    } else if (packet.type == 'siren') {
      final text = packet.payload['text']?.toString() ?? 'Urgent siren alert';
      unawaited(notifications.showSiren(packet.senderName, text));
      unawaited(_playSiren());
    } else if (packet.type == 'call_signal') {
      unawaited(callService.receive(packet.senderId, packet.senderName, packet.payload));
      if (packet.payload['kind'] == 'offer') unawaited(notifications.showIncomingCall(packet.senderName, video: packet.payload['video'] == true));
    } else if (packet.type == 'post') {
      posts.insert(0, CommunityPost(id: id, author: packet.senderName, text: packet.payload['text']?.toString() ?? '', createdAt: DateTime.now()));
    }
    _persist(); notifyListeners();
  }

  Future<bool> sendMessage(MeshPeer peer, String text, {String? replyToId}) async {
    final clean = text.trim(); if (clean.isEmpty || peer.blocked) return false;
    final id = const Uuid().v4(); final m = ChatMessage(id: id, peerId: peer.id, sender: username, text: clean, sentAt: DateTime.now(), mine: true, status: DeliveryStatus.pending, replyToId: replyToId); messages.add(m); notifyListeners();
    final ack = Completer<void>();
    _deliveryAcks[id] = ack;
    var delivered = false;
    for (var attempt = 0; attempt < 3 && !delivered; attempt++) {
      final sent = await mesh.sendToHost(peer.host, 'message', {'id': id, 'text': clean, 'replyToId': replyToId});
      if (!sent) continue;
      try {
        await ack.future.timeout(const Duration(seconds: 3));
        delivered = true;
      } on TimeoutException {
        // Retry: the receiver de-duplicates by message ID.
      }
    }
    if (!delivered) {
      await mesh.sendRoutedPacket(peer.id, 'message', {'id': id, 'text': clean, 'replyToId': replyToId});
      try { await ack.future.timeout(const Duration(seconds: 8)); delivered = true; } on TimeoutException { /* No route currently reaches this peer. */ }
    }
    _deliveryAcks.remove(id);
    m.status = delivered ? DeliveryStatus.delivered : DeliveryStatus.failed;
    await _persist(); notifyListeners(); return delivered;
  }
  Future<void> sendGroupMessage(MeshGroup group, String text) async { final clean = text.trim(); if (clean.isEmpty) return; final id = const Uuid().v4(); messages.add(ChatMessage(id: id, peerId: deviceId, sender: username, text: clean, sentAt: DateTime.now(), mine: true, groupId: group.id)); notifyListeners(); if (group.isPrivate) { for (final memberId in group.members.where((id) => id != deviceId)) { final peer = peers.where((p) => p.id == memberId && p.online && !p.blocked).firstOrNull; final sent = peer != null && await mesh.sendToHost(peer.host, 'group_message', {'id': id, 'groupId': group.id, 'text': clean}); if (!sent) await mesh.sendRoutedPacket(memberId, 'group_message', {'id': id, 'groupId': group.id, 'text': clean}); } } else { await mesh.broadcastRoutedPacket('group_message', {'id': id, 'groupId': group.id, 'text': clean}); } await _persist(); }
  Future<void> postCommunity(String text) async { final clean = text.trim(); if (clean.isEmpty) return; final id = const Uuid().v4(); posts.insert(0, CommunityPost(id: id, author: username, text: clean, createdAt: DateTime.now())); notifyListeners(); await mesh.broadcastRoutedPacket('post', {'id': id, 'text': clean}); await _persist(); }
  Future<void> sendSiren({MeshPeer? peer}) async {
    await _playSiren();
    final payload = {'id': const Uuid().v4(), 'text': peer == null ? 'Community siren activated' : 'Urgent siren from $username'};
    if (peer == null) {
      await mesh.broadcastRoutedPacket('siren', payload);
    } else {
      final sent = peer.online && await mesh.sendToHost(peer.host, 'siren', payload);
      if (!sent) await mesh.sendRoutedPacket(peer.id, 'siren', payload);
    }
  }
  Future<void> triggerSos() async { sosActive = true; Position? position; try { var permission = await Geolocator.checkPermission(); if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission(); if (permission == LocationPermission.always || permission == LocationPermission.whileInUse) position = await Geolocator.getCurrentPosition(locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, timeLimit: Duration(seconds: 8))); } on Object { position = null; } final id = const Uuid().v4(); final text = position == null ? 'SOS ACTIVE — emergency assistance requested.' : 'SOS ACTIVE — location ${position.latitude}, ${position.longitude}'; posts.insert(0, CommunityPost(id: id, author: username, text: text, createdAt: DateTime.now(), emergency: true, latitude: position?.latitude, longitude: position?.longitude)); notifyListeners(); await mesh.broadcastRoutedPacket('sos', {'id': id, 'text': 'Emergency assistance requested.', 'latitude': position?.latitude, 'longitude': position?.longitude}); await _persist(); }
  void stopSos() { sosActive = false; notifyListeners(); }

  Future<void> createGroup(String name, String description, {List<MeshPeer> selectedPeers = const []}) async { final memberIds = <String>{deviceId, ...selectedPeers.where((p) => !p.blocked).map((p) => p.id)}.toList(); final g = MeshGroup(id: const Uuid().v4(), name: name.trim().isEmpty ? 'Mesh Group' : name.trim(), description: description.trim(), members: memberIds, ownerId: deviceId, adminIds: [deviceId]); groups.add(g); notifyListeners(); for (final peer in selectedPeers.where((p) => p.online && !p.blocked)) { await _sendGroupState(g, 'group_invite', only: peer); } await _persist(); }
  Future<void> addGroupMember(MeshGroup group, MeshPeer peer) async { if (!canManageGroup(group) || group.members.contains(peer.id)) return; group.members.add(peer.id); await _sendGroupState(group, 'group_invite', only: peer); await _sendGroupState(group, 'group_update'); await _persist(); notifyListeners(); }
  Future<void> removeGroupMember(MeshGroup group, String memberId) async { if (!canManageGroup(group) || memberId == group.ownerId) return; final removedPeer = peers.where((p) => p.id == memberId && p.online).firstOrNull; group.members.remove(memberId); group.adminIds.remove(memberId); if (removedPeer != null) await _sendGroupState(group, 'group_update', only: removedPeer); await _sendGroupState(group, 'group_update'); await _persist(); notifyListeners(); }
  Future<void> toggleGroupAdmin(MeshGroup group, String memberId) async { if (group.ownerId != deviceId || memberId == group.ownerId || !group.members.contains(memberId)) return; if (group.adminIds.contains(memberId)) { group.adminIds.remove(memberId); } else { group.adminIds.add(memberId); } await _sendGroupState(group, 'group_update'); await _persist(); notifyListeners(); }
  bool canManageGroup(MeshGroup group) => group.ownerId == deviceId || group.adminIds.contains(deviceId);
  Future<void> _sendGroupState(MeshGroup group, String type, {MeshPeer? only}) async { final payload = {'groupId': group.id, 'name': group.name, 'description': group.description, 'members': group.members, 'ownerId': group.ownerId, 'adminIds': group.adminIds, 'isPrivate': group.isPrivate}; final targets = only == null ? peers.where((p) => group.members.contains(p.id) && p.online && !p.blocked) : [only]; for (final peer in targets) { await mesh.sendToHost(peer.host, type, payload); } }
  Future<void> toggleFavorite(MeshPeer peer) async { peer.favorite = !peer.favorite; await _persist(); notifyListeners(); }
  Future<void> toggleBlocked(MeshPeer peer) async { peer.blocked = !peer.blocked; await _persist(); notifyListeners(); }
  Future<bool> startCall(MeshPeer peer, bool video) async {
    callError = null;
    if (callService.active) { callError = 'Finish the current call before starting another one.'; notifyListeners(); return false; }
    if (!peer.online || peer.blocked) { callError = 'This device is not currently reachable.'; notifyListeners(); return false; }
    try {
      final microphone = await Permission.microphone.request();
      if (!microphone.isGranted) { callError = 'Microphone permission is required for calls.'; notifyListeners(); return false; }
      if (video) {
        final camera = await Permission.camera.request();
        if (!camera.isGranted) { callError = 'Camera permission is required for video calls.'; notifyListeners(); return false; }
      }
      await callService.initialize();
      final id = const Uuid().v4();
      await callService.start(targetId: peer.id, targetName: peer.name, withVideo: video, id: id);
      calls.insert(0, CallRecord(id: id, peerName: peer.name, video: video, startedAt: DateTime.now(), outgoing: true));
      await _persist(); notifyListeners(); return true;
    } catch (error) {
      callError = callService.error ?? 'The call could not be started.';
      debugPrint('LinkMesh call start failed: $error');
      notifyListeners(); return false;
    }
  }
  Future<bool> acceptCall() async {
    callError = null;
    try {
      final microphone = await Permission.microphone.request();
      if (!microphone.isGranted) { callError = 'Microphone permission is required for calls.'; await callService.reject(); notifyListeners(); return false; }
      if (callService.video) {
        final camera = await Permission.camera.request();
        if (!camera.isGranted) { callError = 'Camera permission is required for video calls.'; await callService.reject(); notifyListeners(); return false; }
      }
      await callService.initialize();
      final id = callService.callId ?? const Uuid().v4();
      final peerName = callService.peerName ?? 'Peer';
      final isVideo = callService.video;
      await callService.accept();
      calls.insert(0, CallRecord(id: id, peerName: peerName, video: isVideo, startedAt: DateTime.now(), outgoing: false));
      await _persist(); notifyListeners(); return true;
    } catch (error) {
      callError = callService.error ?? 'The incoming call could not be connected.';
      debugPrint('LinkMesh call accept failed: $error');
      notifyListeners(); return false;
    }
  }
  Future<void> _sendCallSignal(String targetId, Map<String, dynamic> signal) async { final peer = peers.where((p) => p.id == targetId && p.online && !p.blocked).firstOrNull; final sent = peer != null && await mesh.sendToHost(peer.host, 'call_signal', signal); if (!sent) await mesh.sendRoutedPacket(targetId, 'call_signal', signal); }
  Future<void> toggleTheme(bool value) async { darkMode = value; await _persist(); notifyListeners(); }
  Future<void> setBackgroundNotifications(bool value) async {
    backgroundNotifications = value;
    await _persist(); notifyListeners();
  }
  Future<void> setAutomaticDirectConnect(bool value) async {
    automaticDirectConnect = value;
    if (!value) await p2p.stopAutomatic();
    await _persist(); notifyListeners();
  }
  Future<void> submitFeedback(String text) async {
    final clean = text.trim();
    if (clean.isEmpty) return;
    feedbackMessages.add(clean);
    await _persist(); notifyListeners();
  }
  Future<void> clearMessages() async { messages.clear(); await _persist(); notifyListeners(); }
  Future<void> clearCallHistory() async { calls.clear(); await _persist(); notifyListeners(); }
  Future<void> clearCommunityHistory() async { posts.clear(); await _persist(); notifyListeners(); }
  Future<bool> exportEncryptedBackup() async {
    final backup = <String, dynamic>{
      'backupVersion': 1,
      'createdAt': DateTime.now().toUtc().toIso8601String(),
      'settings': {'username': username, 'darkMode': darkMode},
      'peers': peers.map((e) => e.toJson()).toList(),
      'messages': messages.map((e) => e.toJson()).toList(),
      'groups': groups.map((e) => e.toJson()).toList(),
      'posts': posts.map((e) => e.toJson()).toList(),
      'calls': calls.map((e) => e.toJson()).toList(),
    };
    final encrypted = await SecureMeshCodec(meshCode).encrypt(backup);
    final path = await FilePicker.platform.saveFile(dialogTitle: 'Save encrypted LinkMesh backup', fileName: 'linkmesh-backup-${DateTime.now().millisecondsSinceEpoch}.lmb', bytes: Uint8List.fromList(utf8.encode(encrypted)));
    return path != null;
  }
  Future<bool> restoreEncryptedBackup() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: ['lmb'], withData: true);
    final bytes = result?.files.single.bytes;
    if (bytes == null) return false;
    final backup = await SecureMeshCodec(meshCode).decrypt(utf8.decode(bytes));
    if (backup == null || backup['backupVersion'] != 1) return false;
    await stopNetwork();
    final settings = backup['settings'] is Map ? Map<String, dynamic>.from(backup['settings'] as Map) : const <String, dynamic>{};
    username = settings['username']?.toString() ?? username;
    darkMode = settings['darkMode'] == true;
    peers..clear()..addAll((backup['peers'] as List? ?? const []).map((e) => MeshPeer.fromJson(Map<String, dynamic>.from(e as Map))));
    messages..clear()..addAll((backup['messages'] as List? ?? const []).map((e) => ChatMessage.fromJson(Map<String, dynamic>.from(e as Map))));
    groups..clear()..addAll((backup['groups'] as List? ?? const []).map((e) => MeshGroup.fromJson(Map<String, dynamic>.from(e as Map))));
    posts..clear()..addAll((backup['posts'] as List? ?? const []).map((e) => CommunityPost.fromJson(Map<String, dynamic>.from(e as Map))));
    calls..clear()..addAll((backup['calls'] as List? ?? const []).map((e) => CallRecord.fromJson(Map<String, dynamic>.from(e as Map))));
    await _persist();
    notifyListeners();
    await startNetwork();
    return true;
  }
  Future<void> deleteMessage(ChatMessage message) async { messages.removeWhere((m) => m.id == message.id); await _persist(); notifyListeners(); }
  Future<void> reactToMessage(MeshPeer peer, ChatMessage message, String emoji) async {
    if (emoji.isEmpty) {
      message.reactions.remove(deviceId);
    } else {
      message.reactions[deviceId] = emoji;
    }
    await mesh.sendToHost(peer.host, 'reaction', {'id': message.id, 'emoji': emoji});
    await _persist(); notifyListeners();
  }
  Future<bool> pickAndSendAttachment(MeshPeer peer) async {
    final result = await FilePicker.platform.pickFiles(withData: true);
    final file = result?.files.single;
    final bytes = file?.bytes;
    if (file == null || bytes == null) return false;
    return sendAttachment(peer, name: file.name, bytes: bytes, localPath: file.path);
  }
  Future<bool> sendAttachment(MeshPeer peer, {required String name, required List<int> bytes, String? localPath, String? mime}) async {
    if (bytes.isEmpty || bytes.length > 5 * 1024 * 1024 || peer.blocked) return false;
    const chunkSize = 24 * 1024;
    final id = const Uuid().v4();
    final cleanName = _safeFileName(name);
    final detectedMime = mime ?? _mimeForName(cleanName);
    var storedPath = localPath;
    if (storedPath == null) {
      final directory = await getApplicationDocumentsDirectory();
      final file = File('${directory.path}/${id}_$cleanName');
      await file.writeAsBytes(bytes, flush: true);
      storedPath = file.path;
    }
    final total = (bytes.length / chunkSize).ceil();
    final message = ChatMessage(id: id, peerId: peer.id, sender: username, text: detectedMime.startsWith('audio/') ? 'Voice note' : cleanName, sentAt: DateTime.now(), mine: true, status: DeliveryStatus.pending, attachmentName: cleanName, attachmentPath: storedPath, attachmentMime: detectedMime, attachmentSize: bytes.length);
    messages.add(message); notifyListeners();
    final ack = Completer<void>();
    _deliveryAcks[id] = ack;
    var sent = await mesh.sendToHost(peer.host, 'file_start', {'id': id, 'name': cleanName, 'mime': detectedMime, 'size': bytes.length, 'totalChunks': total});
    for (var index = 0; index < total && sent; index++) {
      final start = index * chunkSize;
      final end = min(start + chunkSize, bytes.length);
      sent = await mesh.sendToHost(peer.host, 'file_chunk', {'id': id, 'index': index, 'data': base64Encode(bytes.sublist(start, end))});
    }
    if (sent) sent = await mesh.sendToHost(peer.host, 'file_end', {'id': id});
    var delivered = false;
    if (sent) { try { await ack.future.timeout(const Duration(seconds: 15)); delivered = true; } on TimeoutException { /* Keep the transfer failed and retryable. */ } }
    _deliveryAcks.remove(id);
    message.status = delivered ? DeliveryStatus.delivered : DeliveryStatus.failed;
    await _persist(); notifyListeners();
    return delivered;
  }
  Future<void> _finishIncomingAttachment(MeshPacket packet, MeshPeer peer, String host, String id, _IncomingAttachment incoming) async {
    final bytes = <int>[];
    for (var index = 0; index < incoming.totalChunks; index++) { bytes.addAll(incoming.chunks[index]!); }
    if (bytes.length != incoming.size) return;
    final directory = await getApplicationDocumentsDirectory();
    final file = File('${directory.path}/${id}_${incoming.name}');
    await file.writeAsBytes(bytes, flush: true);
    if (!messages.any((message) => message.id == id)) messages.add(ChatMessage(id: id, peerId: peer.id, sender: packet.senderName, text: incoming.mime.startsWith('audio/') ? 'Voice note' : incoming.name, sentAt: DateTime.now(), mine: false, attachmentName: incoming.name, attachmentPath: file.path, attachmentMime: incoming.mime, attachmentSize: incoming.size));
    unawaited(notifications.showMessage(packet.senderName, incoming.mime.startsWith('audio/') ? 'New voice note' : 'New file: ${incoming.name}'));
    if (host.isNotEmpty) await mesh.sendToHost(host, 'ack', {'id': id});
    await _persist(); notifyListeners();
  }
  Future<void> _sendProfilePhoto(String host) async {
    try {
      final bytes = await File(profilePhotoPath).readAsBytes();
      if (bytes.isEmpty || bytes.length > 60 * 1024) return;
      await mesh.sendToHost(host, 'profile_data', {'avatarHash': profilePhotoHash, 'data': base64Encode(bytes)});
    } catch (_) {}
  }
  Future<void> _savePeerPhoto(MeshPeer peer, Map<String, dynamic> payload) async {
    try {
      final bytes = base64Decode(payload['data']?.toString() ?? '');
      final expectedHash = payload['avatarHash']?.toString() ?? '';
      if (bytes.isEmpty || bytes.length > 60 * 1024 || expectedHash.isEmpty || sha256.convert(bytes).toString() != expectedHash) return;
      final directory = await getApplicationDocumentsDirectory();
      final safeId = peer.id.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
      final file = File('${directory.path}/peer_avatar_$safeId.jpg');
      await file.writeAsBytes(bytes, flush: true);
      peer.avatarHash = expectedHash;
      peer.photoPath = file.path;
      _avatarRequests.remove(peer.id);
      await _persist();
      notifyListeners();
    } catch (_) {}
  }
  Future<bool> openSystemSettings() => openAppSettings();
  Future<void> openSirenAccessSettings() async {
    if (!Platform.isAndroid) { await openAppSettings(); return; }
    try { await const MethodChannel('linkmesh/system').invokeMethod<void>('openNotificationPolicySettings'); }
    on PlatformException { await openAppSettings(); }
  }
  Future<bool> retryMessage(ChatMessage message) async {
    if (!message.mine || message.status != DeliveryStatus.failed) return false;
    final peer = peers.where((p) => p.id == message.peerId).firstOrNull;
    if (peer == null || peer.blocked) return false;
    message.status = DeliveryStatus.pending;
    notifyListeners();
    final ack = Completer<void>();
    _deliveryAcks[message.id] = ack;
    final sent = await mesh.sendToHost(peer.host, 'message', {'id': message.id, 'text': message.text, 'replyToId': message.replyToId});
    var delivered = false;
    if (sent) {
      try { await ack.future.timeout(const Duration(seconds: 3)); delivered = true; } on TimeoutException { /* Keep failed status so the user can retry. */ }
    }
    _deliveryAcks.remove(message.id);
    message.status = delivered ? DeliveryStatus.delivered : DeliveryStatus.failed;
    await _persist(); notifyListeners();
    return delivered;
  }
  Future<void> _flushQueuedMessages(MeshPeer peer) async {
    if (!_retryingPeers.add(peer.id)) return;
    try {
      final queued = messages.where((m) => m.mine && m.peerId == peer.id && m.groupId == null && m.status == DeliveryStatus.failed).toList();
      for (final message in queued) {
        if (!peer.online) break;
        await retryMessage(message);
      }
    } finally {
      _retryingPeers.remove(peer.id);
    }
  }
  Future<void> clearLocalData() async { peers.clear(); messages.clear(); posts.clear(); calls.clear(); groups.clear(); await _persist(); notifyListeners(); }

  Future<void> _playSiren() async {
    try { await _alertPlayer.stop(); await _alertPlayer.setAsset('assets/audio/siren.wav'); await _alertPlayer.setLoopMode(LoopMode.one); await _alertPlayer.play(); Future<void>.delayed(const Duration(seconds: 12), () => _alertPlayer.stop()); } catch (_) {}
  }
  Future<void> _handleCallState() async {
    notifyListeners();
    try {
      if (callService.active && !callService.connected) {
        if (!_alertPlayer.playing) { await _alertPlayer.setAsset('assets/audio/call_ringtone.wav'); await _alertPlayer.setLoopMode(LoopMode.one); await _alertPlayer.play(); }
      } else {
        await _alertPlayer.stop();
      }
    } catch (_) {}
  }

  @override void dispose() { _peerSweep?.cancel(); _sub?.cancel(); for (final ack in _deliveryAcks.values) { if (!ack.isCompleted) ack.completeError(StateError('App closed')); } _deliveryAcks.clear(); unawaited(_alertPlayer.dispose()); unawaited(callService.end(notifyPeer: false)); callService.removeListener(_handleCallState); mesh.dispose(); p2p.dispose(); final store = _store; if (store != null) unawaited(_persistQueue.whenComplete(store.close)); super.dispose(); }
}

extension FirstOrNullState<T> on Iterable<T> { T? get firstOrNull => isEmpty ? null : first; }

class _IncomingAttachment {
  _IncomingAttachment({required this.name, required this.mime, required this.size, required this.totalChunks});
  final String name;
  final String mime;
  final int size;
  final int totalChunks;
  final Map<int, List<int>> chunks = {};
}

String _safeFileName(String name) => name.replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_').substring(0, min(name.replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_').length, 80));
String _mimeForName(String name) {
  final lower = name.toLowerCase();
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.gif')) return 'image/gif';
  if (lower.endsWith('.mp4')) return 'video/mp4';
  if (lower.endsWith('.m4a')) return 'audio/mp4';
  if (lower.endsWith('.mp3')) return 'audio/mpeg';
  if (lower.endsWith('.pdf')) return 'application/pdf';
  return 'application/octet-stream';
}

MeshGroup? _groupFromPacket(MeshPacket packet) {
  final payload = packet.payload;
  final id = payload['groupId']?.toString() ?? '';
  final owner = payload['ownerId']?.toString() ?? '';
  final members = List<String>.from(payload['members'] is List ? payload['members'] as List : const <String>[]).toSet().take(100).toList();
  final admins = List<String>.from(payload['adminIds'] is List ? payload['adminIds'] as List : const <String>[]).where(members.contains).toSet().toList();
  if (id.isEmpty || owner.isEmpty || !members.contains(owner)) return null;
  if (!admins.contains(owner)) admins.add(owner);
  return MeshGroup(id: id, name: payload['name']?.toString() ?? 'Private group', description: payload['description']?.toString() ?? '', members: members, ownerId: owner, adminIds: admins, isPrivate: payload['isPrivate'] != false);
}
