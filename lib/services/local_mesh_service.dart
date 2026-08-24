import 'dart:async';
import 'dart:convert';
import 'dart:io';

class MeshPacket {
  MeshPacket({required this.type, required this.senderId, required this.senderName, required this.payload});
  final String type;
  final String senderId;
  final String senderName;
  final Map<String, dynamic> payload;

  Map<String, dynamic> toJson() => {'type': type, 'senderId': senderId, 'senderName': senderName, 'payload': payload};

  factory MeshPacket.fromJson(Map<String, dynamic> json) => MeshPacket(
        type: json['type']?.toString() ?? 'unknown',
        senderId: json['senderId']?.toString() ?? '',
        senderName: json['senderName']?.toString() ?? 'Peer',
        payload: Map<String, dynamic>.from(json['payload'] is Map ? json['payload'] as Map : const {}),
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
  final _packets = StreamController<MeshPacket>.broadcast();

  Stream<MeshPacket> get packets => _packets.stream;
  bool get running => _udp != null && _server != null;

  Future<void> start({required String id, required String name}) async {
    _id = id;
    _name = name;
    if (running) return;
    await stop();
    _udp = await RawDatagramSocket.bind(InternetAddress.anyIPv4, discoveryPort, reuseAddress: true, reusePort: true);
    _udp!.broadcastEnabled = true;
    _udp!.listen((event) {
      if (event != RawSocketEvent.read) return;
      Datagram? datagram;
      while ((datagram = _udp?.receive()) != null) {
        final received = datagram;
        if (received == null) break;
        try {
          final packet = MeshPacket.fromJson(jsonDecode(utf8.decode(received.data)) as Map<String, dynamic>);
          if (packet.senderId.isNotEmpty && packet.senderId != _id) {
            packet.payload['host'] = received.address.address;
            _packets.add(packet);
          }
        } catch (_) {}
      }
    });
    _server = await ServerSocket.bind(InternetAddress.anyIPv4, messagePort, shared: true);
    _server!.listen((socket) {
      utf8.decoder.bind(socket).transform(const LineSplitter()).listen((line) {
        try {
          final packet = MeshPacket.fromJson(jsonDecode(line) as Map<String, dynamic>);
          if (packet.senderId.isNotEmpty && packet.senderId != _id) {
            packet.payload['host'] = socket.remoteAddress.address;
            _packets.add(packet);
          }
        } catch (_) {}
      });
    });
    _beacon = Timer.periodic(const Duration(seconds: 3), (_) => broadcastPresence());
    await broadcastPresence();
  }

  Future<void> broadcastPresence() => broadcastPacket('presence', {'port': messagePort});

  Future<void> broadcastPacket(String type, Map<String, dynamic> payload) async {
    final udp = _udp;
    if (udp == null || _id == null || _name == null) return;
    final packet = MeshPacket(type: type, senderId: _id!, senderName: _name!, payload: payload);
    udp.send(utf8.encode(jsonEncode(packet.toJson())), InternetAddress('255.255.255.255'), discoveryPort);
  }

  Future<bool> sendToHost(String host, String type, Map<String, dynamic> payload) async {
    if (_id == null || _name == null || host.isEmpty) return false;
    Socket? socket;
    try {
      socket = await Socket.connect(host, messagePort, timeout: const Duration(seconds: 3));
      socket.writeln(jsonEncode(MeshPacket(type: type, senderId: _id!, senderName: _name!, payload: payload).toJson()));
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
  }

  Future<void> dispose() async {
    await stop();
    await _packets.close();
  }
}
