import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class NotificationService {
  final FlutterLocalNotificationsPlugin _plugin = FlutterLocalNotificationsPlugin();

  Future<void> initialize() async {
    await _plugin.initialize(const InitializationSettings(android: AndroidInitializationSettings('ic_notification')));
    await _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()?.requestNotificationsPermission();
  }

  Future<void> showMessage(String sender, String preview) => _plugin.show(
        DateTime.now().millisecondsSinceEpoch.remainder(1 << 31),
        sender,
        preview,
        const NotificationDetails(android: AndroidNotificationDetails('linkmesh_messages', 'LinkMesh messages', channelDescription: 'Encrypted nearby messages and files', importance: Importance.high, priority: Priority.high, icon: 'ic_notification')),
      );

  Future<void> showSos(String sender, String details) => _plugin.show(
        DateTime.now().millisecondsSinceEpoch.remainder(1 << 31),
        'SOS from $sender',
        details,
        const NotificationDetails(android: AndroidNotificationDetails('linkmesh_sos', 'Emergency SOS', channelDescription: 'Urgent nearby LinkMesh emergency alerts', importance: Importance.max, priority: Priority.max, icon: 'ic_notification', enableVibration: true)),
      );
}
