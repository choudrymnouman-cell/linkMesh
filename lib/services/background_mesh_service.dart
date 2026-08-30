import 'dart:async';
import 'dart:convert';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'local_mesh_service.dart';
import 'notification_service.dart';
import 'p2p_transport_service.dart';

class BackgroundMeshService {
  static const _queueKey = 'linkmesh_background_packets';

  void initialize() {
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(channelId: 'linkmesh_background_mesh', channelName: 'LinkMesh background mesh', channelDescription: 'Keeps encrypted nearby messaging available while LinkMesh is minimized', onlyAlertOnce: true),
      iosNotificationOptions: const IOSNotificationOptions(showNotification: false, playSound: false),
      foregroundTaskOptions: ForegroundTaskOptions(eventAction: ForegroundTaskEventAction.repeat(15000), autoRunOnBoot: true, autoRunOnMyPackageReplaced: true, allowWakeLock: true, allowWifiLock: true),
    );
  }

  Future<void> start({required String id, required String name, required String meshCode, String avatarHash = '', bool alertsEnabled = true, int ringtoneChoice = 0, bool automaticDirectConnect = true, bool startP2p = false}) async {
    await FlutterForegroundTask.saveData(key: 'linkmesh_id', value: id);
    await FlutterForegroundTask.saveData(key: 'linkmesh_name', value: name);
    await FlutterForegroundTask.saveData(key: 'linkmesh_code', value: meshCode);
    await FlutterForegroundTask.saveData(key: 'linkmesh_avatar_hash', value: avatarHash);
    await FlutterForegroundTask.saveData(key: 'linkmesh_alerts_enabled', value: alertsEnabled);
    await FlutterForegroundTask.saveData(key: 'linkmesh_ringtone_choice', value: ringtoneChoice);
    await FlutterForegroundTask.saveData(key: 'linkmesh_automatic_direct', value: automaticDirectConnect);
    await FlutterForegroundTask.saveData(key: 'linkmesh_start_p2p', value: startP2p);
    if (await FlutterForegroundTask.checkNotificationPermission() != NotificationPermission.granted) await FlutterForegroundTask.requestNotificationPermission();
    if (await FlutterForegroundTask.isRunningService) return;
    await FlutterForegroundTask.startService(serviceId: 40444, notificationTitle: 'LinkMesh is active', notificationText: 'Listening for encrypted nearby messages', notificationInitialRoute: '/', callback: backgroundMeshCallback);
  }

  Future<void> stop() async { if (await FlutterForegroundTask.isRunningService) await FlutterForegroundTask.stopService(); }

  Future<List<MeshPacket>> drainPackets() async {
    final raw = await FlutterForegroundTask.getData<String>(key: _queueKey);
    await FlutterForegroundTask.removeData(key: _queueKey);
    if (raw == null || raw.isEmpty) return [];
    try { return (jsonDecode(raw) as List).map((value) => MeshPacket.fromJson(Map<String, dynamic>.from(value as Map))).toList(); } catch (_) { return []; }
  }
}

@pragma('vm:entry-point')
void backgroundMeshCallback() { FlutterForegroundTask.setTaskHandler(_BackgroundMeshHandler()); }

class _BackgroundMeshHandler extends TaskHandler {
  final LocalMeshService _mesh = LocalMeshService();
  final NotificationService _notifications = NotificationService();
  final P2pTransportService _p2p = P2pTransportService();
  StreamSubscription<MeshPacket>? _subscription;
  bool _alertsEnabled = true;
  int _ringtoneChoice = 0;
  bool _automaticDirectConnect = true;
  bool _startP2p = false;
  String? _id;
  String? _name;
  String? _code;
  String _avatarHash = '';
  final Set<String> _seenSirenIds = {};

  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    _id = await FlutterForegroundTask.getData<String>(key: 'linkmesh_id');
    _name = await FlutterForegroundTask.getData<String>(key: 'linkmesh_name');
    _code = await FlutterForegroundTask.getData<String>(key: 'linkmesh_code');
    _avatarHash = await FlutterForegroundTask.getData<String>(key: 'linkmesh_avatar_hash') ?? '';
    _alertsEnabled = await FlutterForegroundTask.getData<bool>(key: 'linkmesh_alerts_enabled') ?? true;
    _ringtoneChoice = await FlutterForegroundTask.getData<int>(key: 'linkmesh_ringtone_choice') ?? 0;
    _automaticDirectConnect = await FlutterForegroundTask.getData<bool>(key: 'linkmesh_automatic_direct') ?? true;
    _startP2p = await FlutterForegroundTask.getData<bool>(key: 'linkmesh_start_p2p') ?? false;
    if (_id == null || _name == null || _code == null) return;
    await _notifications.initialize(requestPermission: false);
    _mesh.setPresenceData({'avatarHash': _avatarHash});
    _subscription = _mesh.packets.listen(_onPacket);
    await _ensureMeshRunning();
    if (_automaticDirectConnect && _startP2p) {
      try {
        await _p2p.initialize();
        _p2p.addListener(_refreshForDirectLink);
        unawaited(_p2p.startAutomatic(_id!));
      } catch (_) {}
    }
  }

  void _refreshForDirectLink() {
    if (_mesh.running && (_p2p.connected || _p2p.hosting)) unawaited(_mesh.refreshNetwork());
  }

  Future<void> _ensureMeshRunning() async {
    if (_mesh.running || _id == null || _name == null || _code == null) return;
    try {
      _mesh.setPresenceData({'avatarHash': _avatarHash});
      await _mesh.start(id: _id!, name: _name!, meshCode: _code!);
    } catch (_) {}
  }

  Future<void> _onPacket(MeshPacket packet) async {
    if (packet.type == 'siren') {
      final id = packet.payload['id']?.toString() ?? '';
      final host = packet.payload['host']?.toString() ?? '';
      if (id.isNotEmpty) {
        if (packet.payload['relayed'] == true) {
          await _mesh.sendRoutedPacket(packet.senderId, 'ack', {'id': id});
        } else if (host.isNotEmpty) {
          await _mesh.sendToHost(host, 'ack', {'id': id});
        }
        if (!_seenSirenIds.add(id)) return;
        if (_seenSirenIds.length > 500) _seenSirenIds.remove(_seenSirenIds.first);
      }
    }
    final previous = await FlutterForegroundTask.getData<String>(key: BackgroundMeshService._queueKey);
    List<dynamic> queue;
    try { queue = previous == null ? [] : List<dynamic>.from(jsonDecode(previous) as List); } catch (_) { queue = []; }
    queue.add(packet.toJson());
    if (queue.length > 500) queue.removeRange(0, queue.length - 500);
    await FlutterForegroundTask.saveData(key: BackgroundMeshService._queueKey, value: jsonEncode(queue));
    if (packet.type == 'message' || packet.type == 'file_end') {
      final host = packet.payload['host']?.toString() ?? '';
      final id = packet.payload['id']?.toString() ?? '';
      if (id.isNotEmpty && packet.payload['relayed'] == true) {
        await _mesh.sendRoutedPacket(packet.senderId, 'ack', {'id': id});
      } else if (host.isNotEmpty && id.isNotEmpty) {
        await _mesh.sendToHost(host, 'ack', {'id': id});
      }
    }
    if (packet.type == 'pair_request') {
      final host = packet.payload['host']?.toString() ?? '';
      if (host.isNotEmpty) await _mesh.sendToHost(host, 'pair_accept', const {});
    }
    if (!_alertsEnabled) {
      await FlutterForegroundTask.updateService(notificationTitle: 'LinkMesh is active', notificationText: 'Background mesh connected • alerts muted');
      return;
    }
    if (packet.type == 'call_signal' && packet.payload['kind'] == 'offer') {
      await _notifications.showIncomingCall(packet.senderName, video: packet.payload['video'] == true, ringtoneChoice: _ringtoneChoice);
    } else if (packet.type == 'sos') {
      await _notifications.showSos(packet.senderName, packet.payload['text']?.toString() ?? 'Emergency assistance requested');
    } else if (packet.type == 'siren') {
      await _notifications.showSiren(packet.senderName, packet.payload['text']?.toString() ?? 'Urgent siren alert');
    } else if (packet.type == 'message') {
      await _notifications.showMessage(packet.senderName, packet.payload['text']?.toString() ?? 'New encrypted message');
    } else if (packet.type == 'group_message') {
      await _notifications.showMessage(packet.senderName, packet.payload['text']?.toString() ?? 'New group message');
    } else if (packet.type == 'file_end') {
      await _notifications.showMessage(packet.senderName, 'New file received');
    }
    await FlutterForegroundTask.updateService(notificationTitle: packet.type == 'sos' ? 'Emergency SOS received' : 'New LinkMesh activity', notificationText: 'From ${packet.senderName} • tap to open');
  }

  @override
  void onRepeatEvent(DateTime timestamp) {
    unawaited(_ensureMeshRunning().then((_) => _mesh.refreshNetwork()));
    if (_automaticDirectConnect && _startP2p && !_p2p.connected && !_p2p.hosting && !_p2p.automatic && _id != null) unawaited(_p2p.startAutomatic(_id!));
    FlutterForegroundTask.updateService(notificationTitle: 'LinkMesh is active', notificationText: 'Encrypted mesh listening in background');
  }

  @override
  Future<void> onDestroy(DateTime timestamp) async { _p2p.removeListener(_refreshForDirectLink); await _subscription?.cancel(); await _mesh.dispose(); _p2p.dispose(); }
}
