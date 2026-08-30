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

  Future<void> showIncomingCall(String sender, {required bool video, int ringtoneChoice = 0}) {
    final choice = ringtoneChoice.clamp(0, 2).toInt();
    final sounds = ['linkmesh_ringtone', 'linkmesh_ringtone_2', 'linkmesh_ringtone_3'];
    final labels = ['Classic LinkMesh', 'Pulse Alert', 'Dual Tone'];
    return _plugin.show(
        40446,
        video ? 'Incoming video call' : 'Incoming voice call',
        '$sender is calling • tap to answer',
        NotificationDetails(
          android: AndroidNotificationDetails(
            'linkmesh_calls_v3_$choice',
            'LinkMesh calls • ${labels[choice]}',
            channelDescription: 'Incoming encrypted LinkMesh calls',
            importance: Importance.max,
            priority: Priority.max,
            icon: 'ic_notification',
            enableVibration: true,
            playSound: true,
            sound: RawResourceAndroidNotificationSound(sounds[choice]),
            category: AndroidNotificationCategory.call,
            fullScreenIntent: true,
          ),
        ),
      );
  }

  Future<void> showSos(String sender, String details) => _plugin.show(
        40448,
        'SOS from $sender',
        details,
        const NotificationDetails(android: AndroidNotificationDetails('linkmesh_siren_v2', 'Urgent LinkMesh sirens', channelDescription: 'Urgent sirens from trusted LinkMesh devices', importance: Importance.max, priority: Priority.max, icon: 'ic_notification', enableVibration: true, playSound: true, sound: RawResourceAndroidNotificationSound('linkmesh_siren'), category: AndroidNotificationCategory.alarm, fullScreenIntent: true, audioAttributesUsage: AudioAttributesUsage.alarm)),
      );

  Future<void> showSiren(String sender, String details) => _plugin.show(
        40447,
        'URGENT SIREN • $sender',
        details,
        const NotificationDetails(android: AndroidNotificationDetails('linkmesh_siren_v2', 'Urgent LinkMesh sirens', channelDescription: 'Urgent sirens from trusted LinkMesh devices', importance: Importance.max, priority: Priority.max, icon: 'ic_notification', enableVibration: true, playSound: true, sound: RawResourceAndroidNotificationSound('linkmesh_siren'), category: AndroidNotificationCategory.alarm, fullScreenIntent: true, audioAttributesUsage: AudioAttributesUsage.alarm)),
      );

  Future<void> cancelSiren() => _plugin.cancel(40447);
}
