import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';
import 'package:permission_handler/permission_handler.dart';
import 'models/models.dart';
import 'services/local_mesh_service.dart';
import 'services/local_store.dart';
import 'services/secure_mesh_codec.dart';

class AppState extends ChangeNotifier {
  final LocalMeshService mesh = LocalMeshService();
  StreamSubscription<MeshPacket>? _sub;
  Timer? _peerSweep;
  SharedPreferences? _prefs;
  LocalStore? _store;
  Future<void> _persistQueue = Future<void>.value();
  String deviceId = '';
  String username = 'Mesh User';
  String meshCode = '';
  bool onboarded = false;
  bool sosActive = false;
  bool darkMode = false;
  bool networkRunning = false;
  bool initialized = false;
  String? networkError;
  final Map<String, Completer<void>> _deliveryAcks = {};
  final Set<String> _retryingPeers = {};
  final List<MeshPeer> peers = [];
  final List<ChatMessage> messages = [];
  final List<MeshGroup> groups = [];
  final List<CommunityPost> posts = [];
  final List<CallRecord> calls = [];

  Future<void> initialize() async {
    _prefs = await SharedPreferences.getInstance();
    _store = await LocalStore.open();
    deviceId = _prefs!.getString('deviceId') ?? const Uuid().v4();
    username = _prefs!.getString('username') ?? 'Mesh User';
    meshCode = _prefs!.getString('meshCode') ?? '';
    onboarded = _prefs!.getBool('onboarded') ?? false;
    darkMode = _prefs!.getBool('darkMode') ?? false;
    if (onboarded && !validMeshSecret(meshCode)) onboarded = false;
    final migrated = _prefs!.getBool('sqliteMigrated') ?? false;
    await _loadList('peers', (j) => MeshPeer.fromJson(j), peers, migrated);
    await _loadList('messages', (j) => ChatMessage.fromJson(j), messages, migrated);
    await _loadList('groups', (j) => MeshGroup.fromJson(j), groups, migrated);
    await _loadList('posts', (j) => CommunityPost.fromJson(j), posts, migrated);
    await _loadList('calls', (j) => CallRecord.fromJson(j), calls, migrated);
    if (groups.isEmpty) groups.add(MeshGroup(id: 'emergency', name: 'Emergency Mesh Group', description: 'Public localized rescue band', members: [deviceId]));
    if (posts.isEmpty) posts.add(CommunityPost(id: 'welcome', author: 'LinkMesh', text: 'Local mesh ready. Nearby devices can discover this phone while the app is open.', createdAt: DateTime.now()));
    await _prefs!.setString('deviceId', deviceId);
    if (!migrated) { await _persist(); await _prefs!.setBool('sqliteMigrated', true); }
    initialized = true;
    notifyListeners();
    if (onboarded) await startNetwork();
  }

  Future<void> _loadList<T>(String key, T Function(Map<String, dynamic>) parse, List<T> target, bool migrated) async {
    if (migrated) {
      target.addAll((await _store!.readCollection(key)).map(parse));
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
    final p = _prefs; if (p == null) return;
    await p.setString('username', username); await p.setString('meshCode', meshCode); await p.setBool('onboarded', onboarded); await p.setBool('darkMode', darkMode);
    if (messages.length > 2000) messages.removeRange(0, messages.length - 2000);
    await _store!.replaceCollection('peers', peers.map((e) => e.toJson()));
    await _store!.replaceCollection('messages', messages.map((e) => e.toJson()));
    await _store!.replaceCollection('groups', groups.map((e) => e.toJson()));
    await _store!.replaceCollection('posts', posts.take(200).map((e) => e.toJson()));
    await _store!.replaceCollection('calls', calls.take(100).map((e) => e.toJson()));
  }

  bool validMeshSecret(String value) => RegExp(r'^\d{6}$').hasMatch(value.trim()) || RegExp(r'^[a-fA-F0-9]{64}$').hasMatch(value.trim());
  String generateStrongMeshSecret() {
    final random = Random.secure();
    return List<int>.generate(32, (_) => random.nextInt(256)).map((value) => value.toRadixString(16).padLeft(2, '0')).join();
  }
  Future<void> setProfile(String name, String code) async { username = name.trim().isEmpty ? 'Mesh User' : name.trim(); meshCode = code.trim(); if (!validMeshSecret(meshCode)) return; onboarded = true; await _persist(); notifyListeners(); await startNetwork(); }
  Future<void> updateProfile(String name) async { username = name.trim().isEmpty ? username : name.trim(); await _persist(); notifyListeners(); if (networkRunning) { await restartNetwork(); } }
  Future<void> updateMeshCode(String code) async { if (!validMeshSecret(code)) return; meshCode = code.trim(); await _persist(); notifyListeners(); await restartNetwork(); }

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
      await mesh.start(id: deviceId, name: username, meshCode: meshCode);
      networkRunning = true;
      _peerSweep ??= Timer.periodic(const Duration(seconds: 5), (_) => _expirePeers());
    } catch (e) { networkRunning = false; networkError = e.toString(); }
    notifyListeners();
  }
  Future<void> restartNetwork() async { await mesh.stop(); networkRunning = false; notifyListeners(); await startNetwork(); }
  Future<void> stopNetwork() async { await mesh.stop(); networkRunning = false; for (final p in peers) { p.online = false; } await _persist(); notifyListeners(); }

  void _expirePeers() {
    final now = DateTime.now(); bool changed = false;
    for (final p in peers) { final online = p.lastSeen != null && now.difference(p.lastSeen!).inSeconds < 12; if (p.online != online) { p.online = online; changed = true; } }
    if (changed) { _persist(); notifyListeners(); }
  }

  void _onPacket(MeshPacket packet) {
    final host = packet.payload['host']?.toString() ?? '';
    var peer = peers.where((p) => p.id == packet.senderId).firstOrNull;
    if (peer == null) { peer = MeshPeer(id: packet.senderId, name: packet.senderName, host: host, lastSeen: DateTime.now()); peers.add(peer); }
    else { peer.name = packet.senderName; if (host.isNotEmpty) peer.host = host; peer.online = true; peer.lastSeen = DateTime.now(); }
    if (peer.blocked) { _persist(); notifyListeners(); return; }
    if (packet.type == 'presence') unawaited(_flushQueuedMessages(peer));
    final id = packet.payload['id']?.toString() ?? '${DateTime.now().microsecondsSinceEpoch}';
    if (packet.type == 'message') {
      if (!messages.any((m) => m.id == id)) {
        messages.add(ChatMessage(id: id, peerId: packet.senderId, sender: packet.senderName, text: packet.payload['text']?.toString() ?? '', sentAt: DateTime.now(), mine: false, replyToId: packet.payload['replyToId']?.toString()));
      }
      // Always acknowledge retries; the message itself remains de-duplicated.
      if (host.isNotEmpty) unawaited(mesh.sendToHost(host, 'ack', {'id': id}));
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
    } else if (packet.type == 'group_message' && !messages.any((m) => m.id == id)) {
      final groupId = packet.payload['groupId']?.toString();
      if (groupId != null) messages.add(ChatMessage(id: id, peerId: packet.senderId, sender: packet.senderName, text: packet.payload['text']?.toString() ?? '', sentAt: DateTime.now(), mine: false, groupId: groupId));
    } else if (packet.type == 'group_announce') {
      final gid = packet.payload['groupId']?.toString() ?? ''; if (gid.isNotEmpty && !groups.any((g) => g.id == gid)) groups.add(MeshGroup(id: gid, name: packet.payload['name']?.toString() ?? 'Mesh Group', description: packet.payload['description']?.toString() ?? '', members: [deviceId, packet.senderId]));
    } else if (packet.type == 'sos') {
      posts.insert(0, CommunityPost(id: id, author: packet.senderName, text: 'SOS: ${packet.payload['text'] ?? 'Emergency assistance requested.'}', createdAt: DateTime.now(), emergency: true));
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
    _deliveryAcks.remove(id);
    m.status = delivered ? DeliveryStatus.delivered : DeliveryStatus.failed;
    await _persist(); notifyListeners(); return delivered;
  }
  Future<void> sendGroupMessage(MeshGroup group, String text) async { final clean = text.trim(); if (clean.isEmpty) return; final id = const Uuid().v4(); messages.add(ChatMessage(id: id, peerId: deviceId, sender: username, text: clean, sentAt: DateTime.now(), mine: true, groupId: group.id)); notifyListeners(); await mesh.broadcastPacket('group_message', {'id': id, 'groupId': group.id, 'text': clean}); await _persist(); }
  Future<void> postCommunity(String text) async { final clean = text.trim(); if (clean.isEmpty) return; final id = const Uuid().v4(); posts.insert(0, CommunityPost(id: id, author: username, text: clean, createdAt: DateTime.now())); notifyListeners(); await mesh.broadcastPacket('post', {'id': id, 'text': clean}); await _persist(); }
  Future<void> triggerSos() async { sosActive = true; final id = const Uuid().v4(); posts.insert(0, CommunityPost(id: id, author: username, text: 'SOS ACTIVE — emergency assistance requested.', createdAt: DateTime.now(), emergency: true)); notifyListeners(); await mesh.broadcastPacket('sos', {'id': id, 'text': 'Emergency assistance requested.'}); await _persist(); }
  void stopSos() { sosActive = false; notifyListeners(); }

  Future<void> createGroup(String name, String description) async { final g = MeshGroup(id: const Uuid().v4(), name: name.trim().isEmpty ? 'Mesh Group' : name.trim(), description: description.trim(), members: [deviceId]); groups.add(g); notifyListeners(); await mesh.broadcastPacket('group_announce', {'groupId': g.id, 'name': g.name, 'description': g.description}); await _persist(); }
  Future<void> toggleFavorite(MeshPeer peer) async { peer.favorite = !peer.favorite; await _persist(); notifyListeners(); }
  Future<void> toggleBlocked(MeshPeer peer) async { peer.blocked = !peer.blocked; await _persist(); notifyListeners(); }
  Future<void> addCall(MeshPeer peer, bool video) async { calls.insert(0, CallRecord(id: const Uuid().v4(), peerName: peer.name, video: video, startedAt: DateTime.now(), outgoing: true)); await _persist(); notifyListeners(); }
  Future<void> toggleTheme(bool value) async { darkMode = value; await _persist(); notifyListeners(); }
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
  Future<bool> openSystemSettings() => openAppSettings();
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
  Future<void> clearLocalData() async { peers.clear(); messages.clear(); posts.clear(); calls.clear(); groups..clear()..add(MeshGroup(id: 'emergency', name: 'Emergency Mesh Group', description: 'Public localized rescue band', members: [deviceId])); await _persist(); notifyListeners(); }

  @override void dispose() { _peerSweep?.cancel(); _sub?.cancel(); for (final ack in _deliveryAcks.values) { if (!ack.isCompleted) ack.completeError(StateError('App closed')); } _deliveryAcks.clear(); mesh.dispose(); final store = _store; if (store != null) unawaited(_persistQueue.whenComplete(store.close)); super.dispose(); }
}

extension FirstOrNullState<T> on Iterable<T> { T? get firstOrNull => isEmpty ? null : first; }
