import 'dart:async';
import 'dart:io';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:flutter_p2p_connection/flutter_p2p_connection.dart';

class P2pTransportService extends ChangeNotifier {
  final FlutterP2pHost host = FlutterP2pHost();
  final FlutterP2pClient client = FlutterP2pClient();
  final List<BleDiscoveredDevice> discoveredHosts = [];
  final List<StreamSubscription<dynamic>> _subscriptions = [];
  bool initialized = false;
  bool hosting = false;
  bool scanning = false;
  bool connected = false;
  bool automatic = false;
  Timer? _roleTimer;
  bool _connecting = false;
  String status = 'Wi-Fi Direct idle';

  Future<void> initialize() async {
    if (initialized) return;
    if (!Platform.isAndroid) { status = 'Wi-Fi Direct is available on Android'; initialized = true; notifyListeners(); return; }
    await host.initialize();
    await client.initialize();
    _subscriptions.add(host.streamHotspotState().listen((state) { status = 'Host: $state'; connected = state.toString().toLowerCase().contains('connected'); notifyListeners(); }));
    _subscriptions.add(client.streamHotspotState().listen((state) { status = 'Client: $state'; connected = state.toString().toLowerCase().contains('connected'); notifyListeners(); }));
    initialized = true;
  }

  Future<bool> prepare() async {
    if (!Platform.isAndroid) return false;
    if (!initialized) await initialize().timeout(const Duration(seconds: 5));
    if (!await host.checkP2pPermissions()) await host.askP2pPermissions();
    if (!await host.checkBluetoothPermissions()) await host.askBluetoothPermissions();
    if (!await host.checkWifiEnabled()) await host.enableWifiServices();
    if (!await host.checkBluetoothEnabled()) await host.enableBluetoothServices();
    if (!await host.checkLocationEnabled()) await host.enableLocationServices();
    return await host.checkP2pPermissions() && await host.checkBluetoothPermissions() && await host.checkWifiEnabled() && await host.checkBluetoothEnabled();
  }

  Future<void> createGroup() async {
    if (!await prepare()) { status = 'Nearby-device permissions or services are unavailable'; notifyListeners(); return; }
    await client.stopScan();
    final state = await host.createGroup(advertise: true, timeout: const Duration(seconds: 30));
    hosting = true; scanning = false; status = 'Host: $state'; notifyListeners();
  }

  Future<void> discover() async {
    if (!await prepare()) { status = 'Nearby-device permissions or services are unavailable'; notifyListeners(); return; }
    discoveredHosts.clear(); scanning = true; status = 'Scanning with Bluetooth LE…'; notifyListeners();
    await client.startScan((devices) { discoveredHosts..clear()..addAll(devices); notifyListeners(); });
  }

  Future<void> connect(BleDiscoveredDevice device) async {
    if (_connecting) return;
    _connecting = true;
    await client.stopScan(); scanning = false; status = 'Connecting…'; notifyListeners();
    try {
      await client.connectWithDevice(device, timeout: const Duration(seconds: 30));
      connected = true; status = 'Connected automatically with Wi-Fi Direct'; notifyListeners();
    } finally { _connecting = false; }
  }

  Future<void> startAutomatic(String deviceId) async {
    if (automatic || connected || hosting) return;
    automatic = true;
    await _runAutomaticRole(deviceId, 0);
  }

  Future<void> _runAutomaticRole(String deviceId, int cycle) async {
    if (!automatic || connected) return;
    if (!await prepare()) { automatic = false; return; }
    final preferHost = (deviceId.hashCode + cycle).isEven;
    if (preferHost) {
      await Future<void>.delayed(Duration(milliseconds: 600 + Random().nextInt(1400)));
      if (!automatic || connected) return;
      try { await createGroup(); } catch (_) { hosting = false; }
    } else {
      try {
        discoveredHosts.clear(); scanning = true; status = 'Finding LinkMesh phones automatically…'; notifyListeners();
        await client.startScan((devices) {
          discoveredHosts..clear()..addAll(devices);
          if (automatic && devices.isNotEmpty && !connected && !_connecting) unawaited(connect(devices.first));
          notifyListeners();
        });
      } catch (_) { scanning = false; }
    }
    _roleTimer?.cancel();
    _roleTimer = Timer(const Duration(seconds: 18), () async {
      if (!automatic || connected) return;
      try { await client.stopScan(); } catch (_) {}
      try { if (hosting) await host.removeGroup(); } catch (_) {}
      hosting = false; scanning = false;
      await _runAutomaticRole(deviceId, cycle + 1);
    });
  }

  Future<void> stopAutomatic() async {
    automatic = false;
    _roleTimer?.cancel(); _roleTimer = null;
    try { await client.stopScan(); } catch (_) {}
  }

  Future<void> disconnect() async {
    if (!Platform.isAndroid) return;
    if (hosting) { await host.removeGroup(); } else { await client.disconnect(); }
    hosting = false; scanning = false; status = 'Wi-Fi Direct idle'; notifyListeners();
  }

  @override
  void dispose() { _roleTimer?.cancel(); for (final subscription in _subscriptions) { unawaited(subscription.cancel()); } if (Platform.isAndroid) { unawaited(host.dispose()); unawaited(client.dispose()); } super.dispose(); }
}
