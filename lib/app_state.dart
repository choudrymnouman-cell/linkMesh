import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';
import 'package:permission_handler/permission_handler.dart';
import 'models/models.dart';
import 'services/local_mesh_service.dart';

class AppState extends ChangeNotifier {
  final LocalMeshService mesh = LocalMeshService();
  StreamSubscription<MeshPacket>? _sub;
  Timer? _peerSweep;
  SharedPreferences? _prefs;
  String deviceId = '';
  String username = 'Mesh User';
  bool onboarded = false;
  bool sosActive = false;
  bool darkMode = false;
  bool networkRunning = false;
  bool initialized = false;
  String? networkError;
  final Map<String, Completer<void>> _deliveryAcks = {};
  final List<MeshPeer> peers = [];
  final List<ChatMessage> messages = [];
  final List<MeshGroup> groups = [];
  final List<CommunityPost> posts = [];
  final List<CallRecord> calls = [];

  Future<void> initialize() async {
    _prefs = await SharedPreferences.getInstance();
    deviceId = _prefs!.getString('deviceId') ?? const Uuid().v4();
    username = _prefs!.getString('username') ?? 'Mesh User';
    onboarded = _prefs!.getBool('onboarded') ?? false;
    darkMode = _prefs!.getBool('darkMode') ?? false;
    _decodeList('peers', (j) => MeshPeer.fromJson(j), peers);
    _decodeList('messages', (j) => ChatMessage.fromJson(j), messages);
    _decodeList('groups', (j) => MeshGroup.fromJson(j), groups);
    _decodeList('posts', (j) => CommunityPost.fromJson(j), posts);
    _decodeList('calls', (j) => CallRecord.fromJson(j), calls);
    if (groups.isEmpty) groups.add(MeshGroup(id: 'emergency', name: 'Emergency Mesh Group', description: 'Public localized rescue band', members: [deviceId]));
    if (posts.isEmpty) posts.add(CommunityPost(id: 'welcome', author: 'LinkMesh', text: 'Local mesh ready. Nearby devices can discover this phone while the app is open.', createdAt: DateTime.now()));
    await _prefs!.setString('deviceId', deviceId);
    initialized = true;
    notifyListeners();
    if (onboarded) await startNetwork();
  }

  void _decodeList<T>(String key, T Function(Map<String, dynamic>) parse, List<T> target) {
    final raw = _prefs?.getString(key); if (raw == null) return;
    try { target.addAll((jsonDecode(raw) as List).map((e) => parse(Map<String, dynamic>.from(e as Map)))); } catch (_) {}
  }
  Future<void> _persist() async {
    final p = _prefs; if (p == null) return;
    await p.setString('username', username); await p.setBool('onboarded', onboarded); await p.setBool('darkMode', darkMode);
    await p.setString('peers', jsonEncode(peers.map((e) => e.toJson()).toList()));
    if (messages.length > 2000) messages.removeRange(0, messages.length - 2000);
    await p.setString('messages', jsonEncode(messages.map((e) => e.toJson()).toList()));
    await p.setString('groups', jsonEncode(groups.map((e) => e.toJson()).toList()));
    await p.setString('posts', jsonEncode(posts.take(200).map((e) => e.toJson()).toList()));
    await p.setString('calls', jsonEncode(calls.take(100).map((e) => e.toJson()).toList()));
  }

  Future<void> setProfile(String name) async { username = name.trim().isEmpty ? 'Mesh User' : name.trim(); onboarded = true; await _persist(); notifyListeners(); await startNetwork(); }
  Future<void> updateProfile(String name) async { username = name.trim().isEmpty ? username : name.trim(); await _persist(); notifyListeners(); if (networkRunning) { await restartNetwork(); } }

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
      await mesh.start(id: deviceId, name: username);
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
    final id = packet.payload['id']?.toString() ?? '${DateTime.now().microsecondsSinceEpoch}';
    if (packet.type == 'message') {
      if (!messages.any((m) => m.id == id)) {
        messages.add(ChatMessage(id: id, peerId: packet.senderId, sender: packet.senderName, text: packet.payload['text']?.toString() ?? '', sentAt: DateTime.now(), mine: false));
      }
      // Always acknowledge retries; the message itself remains de-duplicated.
      if (host.isNotEmpty) unawaited(mesh.sendToHost(host, 'ack', {'id': id}));
    } else if (packet.type == 'ack') {
      _deliveryAcks.remove(id)?.complete();
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

  Future<bool> sendMessage(MeshPeer peer, String text) async {
    final clean = text.trim(); if (clean.isEmpty || peer.blocked) return false;
    final id = const Uuid().v4(); final m = ChatMessage(id: id, peerId: peer.id, sender: username, text: clean, sentAt: DateTime.now(), mine: true, status: DeliveryStatus.pending); messages.add(m); notifyListeners();
    final ack = Completer<void>();
    _deliveryAcks[id] = ack;
    var delivered = false;
    for (var attempt = 0; attempt < 3 && !delivered; attempt++) {
      final sent = await mesh.sendToHost(peer.host, 'message', {'id': id, 'text': clean});
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
  Future<bool> openSystemSettings() => openAppSettings();
  Future<bool> retryMessage(ChatMessage message) async {
    if (!message.mine || message.status != DeliveryStatus.failed) return false;
    final peer = peers.where((p) => p.id == message.peerId).firstOrNull;
    if (peer == null || peer.blocked) return false;
    message.status = DeliveryStatus.pending;
    notifyListeners();
    final ack = Completer<void>();
    _deliveryAcks[message.id] = ack;
    final sent = await mesh.sendToHost(peer.host, 'message', {'id': message.id, 'text': message.text});
    var delivered = false;
    if (sent) {
      try { await ack.future.timeout(const Duration(seconds: 3)); delivered = true; } on TimeoutException { /* Keep failed status so the user can retry. */ }
    }
    _deliveryAcks.remove(message.id);
    message.status = delivered ? DeliveryStatus.delivered : DeliveryStatus.failed;
    await _persist(); notifyListeners();
    return delivered;
  }
  Future<void> clearLocalData() async { peers.clear(); messages.clear(); posts.clear(); calls.clear(); groups..clear()..add(MeshGroup(id: 'emergency', name: 'Emergency Mesh Group', description: 'Public localized rescue band', members: [deviceId])); await _persist(); notifyListeners(); }

  @override void dispose() { _peerSweep?.cancel(); _sub?.cancel(); for (final ack in _deliveryAcks.values) { if (!ack.isCompleted) ack.completeError(StateError('App closed')); } _deliveryAcks.clear(); mesh.dispose(); super.dispose(); }
}

extension FirstOrNullState<T> on Iterable<T> { T? get firstOrNull => isEmpty ? null : first; }
