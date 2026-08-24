import 'dart:async';
import 'dart:convert';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'local_mesh_service.dart';

class BackgroundMeshService {
  static const _queueKey = 'linkmesh_background_packets';

  void initialize() {
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(channelId: 'linkmesh_background_mesh', channelName: 'LinkMesh background mesh', channelDescription: 'Keeps encrypted nearby messaging available while LinkMesh is minimized', onlyAlertOnce: true),
      iosNotificationOptions: const IOSNotificationOptions(showNotification: false, playSound: false),
      foregroundTaskOptions: ForegroundTaskOptions(eventAction: ForegroundTaskEventAction.repeat(15000), autoRunOnBoot: true, autoRunOnMyPackageReplaced: true, allowWakeLock: true, allowWifiLock: true),
    );
  }

  Future<void> start({required String id, required String name, required String meshCode}) async {
    await FlutterForegroundTask.saveData(key: 'linkmesh_id', value: id);
    await FlutterForegroundTask.saveData(key: 'linkmesh_name', value: name);
    await FlutterForegroundTask.saveData(key: 'linkmesh_code', value: meshCode);
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
  StreamSubscription<MeshPacket>? _subscription;

  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    final id = await FlutterForegroundTask.getData<String>(key: 'linkmesh_id');
    final name = await FlutterForegroundTask.getData<String>(key: 'linkmesh_name');
    final code = await FlutterForegroundTask.getData<String>(key: 'linkmesh_code');
    if (id == null || name == null || code == null) return;
    _subscription = _mesh.packets.listen(_onPacket);
    await _mesh.start(id: id, name: name, meshCode: code);
  }

  Future<void> _onPacket(MeshPacket packet) async {
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
    await FlutterForegroundTask.updateService(notificationTitle: packet.type == 'sos' ? 'Emergency SOS received' : 'New LinkMesh activity', notificationText: 'From ${packet.senderName} • tap to open');
  }

  @override
  void onRepeatEvent(DateTime timestamp) { FlutterForegroundTask.updateService(notificationTitle: 'LinkMesh is active', notificationText: 'Encrypted mesh listening in background'); }

  @override
  Future<void> onDestroy(DateTime timestamp) async { await _subscription?.cancel(); await _mesh.dispose(); }
}
