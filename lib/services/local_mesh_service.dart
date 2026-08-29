import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'secure_mesh_codec.dart';

class MeshPacket {
  MeshPacket({required this.type, required this.senderId, required this.senderName, required this.payload, this.routeId, this.targetId, this.hopsRemaining = 0, List<String>? relayPath}) : relayPath = relayPath ?? [];
  final String type;
  final String senderId;
  final String senderName;
  final Map<String, dynamic> payload;
  final String? routeId;
  final String? targetId;
  final int hopsRemaining;
  final List<String> relayPath;

  Map<String, dynamic> toJson() => {'type': type, 'senderId': senderId, 'senderName': senderName, 'payload': payload, if (routeId != null) 'routeId': routeId, if (targetId != null) 'targetId': targetId, 'hopsRemaining': hopsRemaining, 'relayPath': relayPath};

  factory MeshPacket.fromJson(Map<String, dynamic> json) => MeshPacket(
        type: json['type']?.toString() ?? 'unknown',
        senderId: json['senderId']?.toString() ?? '',
        senderName: json['senderName']?.toString() ?? 'Peer',
        payload: Map<String, dynamic>.from(json['payload'] is Map ? json['payload'] as Map : const {}),
        routeId: json['routeId']?.toString(),
        targetId: json['targetId']?.toString(),
        hopsRemaining: int.tryParse('${json['hopsRemaining']}') ?? 0,
        relayPath: List<String>.from(json['relayPath'] is List ? json['relayPath'] as List : const <String>[]),
      );
}

class LocalMeshService {
  static const int discoveryPort = 40444;
  static const int messagePort = 40445;
  RawDatagramSocket? _udp;
  ServerSocket? _server;
  Timer? _beacon;
  String? _id;
  String? _name;
  SecureMeshCodec? _codec;
  Map<String, dynamic> _presenceData = const {};
  List<InternetAddress> _broadcastAddresses = [InternetAddress('255.255.255.255')];
  final _packets = StreamController<MeshPacket>.broadcast();
  final Map<String, DateTime> _seenRoutes = {};
  static const int maxPacketBytes = 64 * 1024;
  static const int maxRelayHops = 4;

  Stream<MeshPacket> get packets => _packets.stream;
  bool get running => _udp != null && _server != null;

  Future<void> start({required String id, required String name, required String meshCode}) async {
    if (running) await stop();
    _id = id;
    _name = name;
    _codec = SecureMeshCodec(meshCode);
    await _refreshBroadcastAddresses();
    _udp = await RawDatagramSocket.bind(InternetAddress.anyIPv4, discoveryPort, reuseAddress: true, reusePort: true);
    _udp!.broadcastEnabled = true;
    _udp!.listen((event) async {
      if (event != RawSocketEvent.read) return;
      Datagram? datagram;
      while ((datagram = _udp?.receive()) != null) {
        final received = datagram;
        if (received == null) break;
        try {
          if (received.data.isEmpty || received.data.length > maxPacketBytes) continue;
          final decoded = await _codec?.decrypt(utf8.decode(received.data));
          if (decoded == null) continue;
          final packet = MeshPacket.fromJson(decoded);
          await _handlePacket(packet, received.address.address);
        } catch (_) {}
      }
    });
    _server = await ServerSocket.bind(InternetAddress.anyIPv4, messagePort, shared: true);
    _server!.listen((socket) {
      utf8.decoder.bind(socket).transform(const LineSplitter()).listen((line) async {
        try {
          if (line.isEmpty || utf8.encode(line).length > maxPacketBytes) {
            socket.destroy();
            return;
          }
          final decoded = await _codec?.decrypt(line);
          if (decoded == null) return;
          final packet = MeshPacket.fromJson(decoded);
          await _handlePacket(packet, socket.remoteAddress.address);
        } catch (_) {}
      });
    });
    _beacon = Timer.periodic(const Duration(seconds: 1), (_) => broadcastPresence());
    await broadcastPresence();
    for (final delay in const [250, 700, 1400]) {
      Future<void>.delayed(Duration(milliseconds: delay), () async {
        if (running) await broadcastPresence();
      });
    }
  }

  void setPresenceData(Map<String, dynamic> data) {
    _presenceData = Map<String, dynamic>.from(data);
    if (running) unawaited(broadcastPresence());
  }

  Future<void> refreshNetwork() async {
    if (!running) return;
    await _refreshBroadcastAddresses();
    await broadcastPresence();
  }

  Future<void> broadcastPresence() => broadcastPacket('presence', {'port': messagePort, ..._presenceData});

  Future<void> _refreshBroadcastAddresses() async {
    final addresses = <String>{'255.255.255.255'};
    try {
      final interfaces = await NetworkInterface.list(type: InternetAddressType.IPv4, includeLoopback: false);
      for (final interface in interfaces) {
        for (final address in interface.addresses) {
          final parts = address.address.split('.');
          if (parts.length == 4) addresses.add('${parts[0]}.${parts[1]}.${parts[2]}.255');
        }
      }
    } catch (_) {}
    _broadcastAddresses = addresses.map(InternetAddress.new).toList(growable: false);
  }

  Future<void> broadcastPacket(String type, Map<String, dynamic> payload) async {
    final udp = _udp;
    if (udp == null || _id == null || _name == null) return;
    final packet = MeshPacket(type: type, senderId: _id!, senderName: _name!, payload: payload);
    final data = utf8.encode(await _codec!.encrypt(packet.toJson()));
    if (data.length > maxPacketBytes) throw ArgumentError('Packet is too large');
    for (final address in _broadcastAddresses) {
      udp.send(data, address, discoveryPort);
    }
  }

  Future<void> sendRoutedPacket(String targetId, String type, Map<String, dynamic> payload) async {
    final udp = _udp;
    if (udp == null || _id == null || _name == null || targetId.isEmpty) return;
    final routeId = _newRouteId();
    _seenRoutes[routeId] = DateTime.now();
    final packet = MeshPacket(type: type, senderId: _id!, senderName: _name!, payload: payload, routeId: routeId, targetId: targetId, hopsRemaining: maxRelayHops, relayPath: [_id!]);
    await _sendDatagram(packet);
  }

  Future<void> broadcastRoutedPacket(String type, Map<String, dynamic> payload) async {
    if (_udp == null || _id == null || _name == null) return;
    final routeId = _newRouteId();
    _seenRoutes[routeId] = DateTime.now();
    final packet = MeshPacket(type: type, senderId: _id!, senderName: _name!, payload: payload, routeId: routeId, hopsRemaining: maxRelayHops, relayPath: [_id!]);
    await _sendDatagram(packet);
  }

  Future<bool> sendToHost(String host, String type, Map<String, dynamic> payload) async {
    if (_id == null || _name == null || host.isEmpty) return false;
    Socket? socket;
    try {
      socket = await Socket.connect(host, messagePort, timeout: const Duration(milliseconds: 900));
      final encoded = await _codec!.encrypt(MeshPacket(type: type, senderId: _id!, senderName: _name!, payload: payload).toJson());
      if (utf8.encode(encoded).length > maxPacketBytes) return false;
      socket.writeln(encoded);
      await socket.flush();
      return true;
    } catch (_) {
      return false;
    } finally {
      await socket?.close();
    }
  }

  Future<void> stop() async {
    _beacon?.cancel();
    _beacon = null;
    _udp?.close();
    _udp = null;
    await _server?.close();
    _server = null;
    _codec = null;
    _seenRoutes.clear();
  }

  Future<void> _handlePacket(MeshPacket packet, String immediateHost) async {
    if (packet.senderId.isEmpty || packet.senderId == _id) return;
    final routeId = packet.routeId;
    if (routeId != null) {
      _pruneRoutes();
      if (_seenRoutes.containsKey(routeId)) return;
      _seenRoutes[routeId] = DateTime.now();
      if (packet.targetId != null && packet.targetId != _id) {
        await _relay(packet);
        return;
      }
    }
    packet.payload['host'] = immediateHost;
    packet.payload['relayed'] = packet.relayPath.length > 1;
    _packets.add(packet);
    if (routeId != null && packet.targetId == null) await _relay(packet);
  }

  Future<void> _relay(MeshPacket packet) async {
    if (packet.hopsRemaining <= 0 || _id == null || packet.relayPath.contains(_id)) return;
    final relayed = MeshPacket(type: packet.type, senderId: packet.senderId, senderName: packet.senderName, payload: Map<String, dynamic>.from(packet.payload)..remove('host')..remove('relayed'), routeId: packet.routeId, targetId: packet.targetId, hopsRemaining: packet.hopsRemaining - 1, relayPath: [...packet.relayPath, _id!]);
    await _sendDatagram(relayed);
  }

  Future<void> _sendDatagram(MeshPacket packet) async {
    final udp = _udp;
    final codec = _codec;
    if (udp == null || codec == null) return;
    final data = utf8.encode(await codec.encrypt(packet.toJson()));
    if (data.length > maxPacketBytes) throw ArgumentError('Packet is too large');
    for (final address in _broadcastAddresses) {
      udp.send(data, address, discoveryPort);
    }
  }

  String _newRouteId() => '${_id ?? 'node'}-${DateTime.now().microsecondsSinceEpoch}-${Random.secure().nextInt(1 << 32)}';

  void _pruneRoutes() {
    final cutoff = DateTime.now().subtract(const Duration(minutes: 5));
    _seenRoutes.removeWhere((_, seenAt) => seenAt.isBefore(cutoff));
  }

  Future<void> dispose() async {
    await stop();
    await _packets.close();
  }
}
