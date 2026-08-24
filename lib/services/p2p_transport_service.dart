import 'dart:async';
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
  String status = 'Wi-Fi Direct idle';

  Future<void> initialize() async {
    if (initialized) return;
    await host.initialize();
    await client.initialize();
    _subscriptions.add(host.streamHotspotState().listen((state) { status = 'Host: $state'; notifyListeners(); }));
    _subscriptions.add(client.streamHotspotState().listen((state) { status = 'Client: $state'; notifyListeners(); }));
    initialized = true;
  }

  Future<bool> prepare() async {
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
    await client.stopScan(); scanning = false; status = 'Connecting…'; notifyListeners();
    await client.connectWithDevice(device, timeout: const Duration(seconds: 30));
    status = 'Connected to Wi-Fi Direct group'; notifyListeners();
  }

  Future<void> disconnect() async {
    if (hosting) await host.removeGroup(); else await client.disconnect();
    hosting = false; scanning = false; status = 'Wi-Fi Direct idle'; notifyListeners();
  }

  @override
  void dispose() { for (final subscription in _subscriptions) { unawaited(subscription.cancel()); } unawaited(host.dispose()); unawaited(client.dispose()); super.dispose(); }
}
