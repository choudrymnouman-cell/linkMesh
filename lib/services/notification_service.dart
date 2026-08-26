import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class NotificationService {
  final FlutterLocalNotificationsPlugin _plugin = FlutterLocalNotificationsPlugin();

  Future<void> initialize({bool requestPermission = true}) async {
    await _plugin.initialize(const InitializationSettings(android: AndroidInitializationSettings('ic_notification')));
    if (requestPermission) await _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()?.requestNotificationsPermission();
  }

  Future<void> showMessage(String sender, String preview) => _plugin.show(
        DateTime.now().millisecondsSinceEpoch.remainder(1 << 31),
        sender,
        preview,
        const NotificationDetails(android: AndroidNotificationDetails('linkmesh_messages', 'LinkMesh messages', channelDescription: 'Encrypted nearby messages and files', importance: Importance.high, priority: Priority.high, icon: 'ic_notification')),
      );

  Future<void> showIncomingCall(String sender, {required bool video}) => _plugin.show(
        40446,
        video ? 'Incoming video call' : 'Incoming voice call',
        '$sender is calling • tap to answer',
        const NotificationDetails(
          android: AndroidNotificationDetails(
            'linkmesh_calls',
            'LinkMesh calls',
            channelDescription: 'Incoming encrypted LinkMesh calls',
            importance: Importance.max,
            priority: Priority.max,
            icon: 'ic_notification',
            enableVibration: true,
            category: AndroidNotificationCategory.call,
            fullScreenIntent: true,
          ),
        ),
      );

  Future<void> showSos(String sender, String details) => _plugin.show(
        DateTime.now().millisecondsSinceEpoch.remainder(1 << 31),
        'SOS from $sender',
        details,
        const NotificationDetails(android: AndroidNotificationDetails('linkmesh_sos', 'Emergency SOS', channelDescription: 'Urgent nearby LinkMesh emergency alerts', importance: Importance.max, priority: Priority.max, icon: 'ic_notification', enableVibration: true)),
      );
}
