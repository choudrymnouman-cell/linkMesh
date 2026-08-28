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
        const NotificationDetails(android: AndroidNotificationDetails('linkmesh_messages_v2', 'LinkMesh messages', channelDescription: 'Encrypted nearby messages and files', importance: Importance.high, priority: Priority.high, icon: 'ic_notification', playSound: true, sound: RawResourceAndroidNotificationSound('linkmesh_message'))),
      );

  Future<void> showIncomingCall(String sender, {required bool video}) => _plugin.show(
        40446,
        video ? 'Incoming video call' : 'Incoming voice call',
        '$sender is calling • tap to answer',
        const NotificationDetails(
          android: AndroidNotificationDetails(
            'linkmesh_calls_v2',
            'LinkMesh calls',
            channelDescription: 'Incoming encrypted LinkMesh calls',
            importance: Importance.max,
            priority: Priority.max,
            icon: 'ic_notification',
            enableVibration: true,
            playSound: true,
            sound: RawResourceAndroidNotificationSound('linkmesh_ringtone'),
            category: AndroidNotificationCategory.call,
            fullScreenIntent: true,
          ),
        ),
      );

  Future<void> showSos(String sender, String details) => _plugin.show(
        DateTime.now().millisecondsSinceEpoch.remainder(1 << 31),
        'SOS from $sender',
        details,
        const NotificationDetails(android: AndroidNotificationDetails('linkmesh_siren_v2', 'Urgent LinkMesh sirens', channelDescription: 'Urgent sirens from trusted LinkMesh devices', importance: Importance.max, priority: Priority.max, icon: 'ic_notification', enableVibration: true, playSound: true, sound: RawResourceAndroidNotificationSound('linkmesh_siren'), category: AndroidNotificationCategory.alarm, fullScreenIntent: true, bypassDnd: true, audioAttributesUsage: AudioAttributesUsage.alarm)),
      );

  Future<void> showSiren(String sender, String details) => _plugin.show(
        DateTime.now().millisecondsSinceEpoch.remainder(1 << 31),
        'URGENT SIREN • $sender',
        details,
        const NotificationDetails(android: AndroidNotificationDetails('linkmesh_siren_v2', 'Urgent LinkMesh sirens', channelDescription: 'Urgent sirens from trusted LinkMesh devices', importance: Importance.max, priority: Priority.max, icon: 'ic_notification', enableVibration: true, playSound: true, sound: RawResourceAndroidNotificationSound('linkmesh_siren'), category: AndroidNotificationCategory.alarm, fullScreenIntent: true, bypassDnd: true, audioAttributesUsage: AudioAttributesUsage.alarm)),
      );
}
