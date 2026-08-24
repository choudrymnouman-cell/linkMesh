enum DeliveryStatus { pending, delivered, failed }

class MeshPeer {
  MeshPeer({
    required this.id,
    required this.name,
    required this.host,
    this.online = true,
    this.lastSeen,
    this.favorite = false,
    this.blocked = false,
  });

  final String id;
  String name;
  String host;
  bool online;
  DateTime? lastSeen;
  bool favorite;
  bool blocked;

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'host': host,
        'online': online,
        'lastSeen': lastSeen?.toIso8601String(),
        'favorite': favorite,
        'blocked': blocked,
      };

  factory MeshPeer.fromJson(Map<String, dynamic> json) => MeshPeer(
        id: '${json['id']}',
        name: '${json['name']}',
        host: '${json['host']}',
        online: json['online'] == true,
        lastSeen: DateTime.tryParse('${json['lastSeen'] ?? ''}'),
        favorite: json['favorite'] == true,
        blocked: json['blocked'] == true,
      );
}

class ChatMessage {
  ChatMessage({
    required this.id,
    required this.peerId,
    required this.sender,
    required this.text,
    required this.sentAt,
    required this.mine,
    this.status = DeliveryStatus.delivered,
    this.groupId,
    this.replyToId,
    Map<String, String>? reactions,
    this.attachmentName,
    this.attachmentPath,
    this.attachmentMime,
    this.attachmentSize,
  }) : reactions = reactions ?? {};

  final String id;
  final String peerId;
  final String sender;
  final String text;
  final DateTime sentAt;
  final bool mine;
  DeliveryStatus status;
  final String? groupId;
  final String? replyToId;
  final Map<String, String> reactions;
  final String? attachmentName;
  final String? attachmentPath;
  final String? attachmentMime;
  final int? attachmentSize;

  Map<String, dynamic> toJson() => {
        'id': id,
        'peerId': peerId,
        'sender': sender,
        'text': text,
        'sentAt': sentAt.toIso8601String(),
        'mine': mine,
        'status': status.name,
        'groupId': groupId,
        'replyToId': replyToId,
        'reactions': reactions,
        'attachmentName': attachmentName,
        'attachmentPath': attachmentPath,
        'attachmentMime': attachmentMime,
        'attachmentSize': attachmentSize,
      };

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    var status = DeliveryStatus.delivered;
    for (final value in DeliveryStatus.values) {
      if (value.name == json['status']) {
        status = value;
        break;
      }
    }
    return ChatMessage(
      id: '${json['id']}',
      peerId: '${json['peerId']}',
      sender: '${json['sender']}',
      text: '${json['text']}',
      sentAt: DateTime.tryParse('${json['sentAt']}') ?? DateTime.now(),
      mine: json['mine'] == true,
      status: status,
      groupId: json['groupId']?.toString(),
      replyToId: json['replyToId']?.toString(),
      reactions: Map<String, String>.from(json['reactions'] is Map ? json['reactions'] as Map : const {}),
      attachmentName: json['attachmentName']?.toString(),
      attachmentPath: json['attachmentPath']?.toString(),
      attachmentMime: json['attachmentMime']?.toString(),
      attachmentSize: json['attachmentSize'] is num ? (json['attachmentSize'] as num).toInt() : null,
    );
  }
}

class MeshGroup {
  MeshGroup({
    required this.id,
    required this.name,
    required this.description,
    required this.members,
    this.ownerId = '',
    List<String>? adminIds,
    this.isPrivate = true,
  }) : adminIds = adminIds ?? [];

  final String id;
  String name;
  String description;
  final List<String> members;
  String ownerId;
  final List<String> adminIds;
  bool isPrivate;

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'description': description,
        'members': members,
        'ownerId': ownerId,
        'adminIds': adminIds,
        'isPrivate': isPrivate,
      };

  factory MeshGroup.fromJson(Map<String, dynamic> json) => MeshGroup(
        id: '${json['id']}',
        name: '${json['name']}',
        description: '${json['description']}',
        members: List<String>.from(json['members'] ?? const <String>[]),
        ownerId: json['ownerId']?.toString() ?? '',
        adminIds: List<String>.from(json['adminIds'] ?? const <String>[]),
        isPrivate: json.containsKey('isPrivate') ? json['isPrivate'] != false : false,
      );
}

class CommunityPost {
  CommunityPost({
    required this.id,
    required this.author,
    required this.text,
    required this.createdAt,
    this.emergency = false,
    this.latitude,
    this.longitude,
  });

  final String id;
  final String author;
  final String text;
  final DateTime createdAt;
  final bool emergency;
  final double? latitude;
  final double? longitude;

  Map<String, dynamic> toJson() => {
        'id': id,
        'author': author,
        'text': text,
        'createdAt': createdAt.toIso8601String(),
        'emergency': emergency,
        'latitude': latitude,
        'longitude': longitude,
      };

  factory CommunityPost.fromJson(Map<String, dynamic> json) => CommunityPost(
        id: '${json['id']}',
        author: '${json['author']}',
        text: '${json['text']}',
        createdAt: DateTime.tryParse('${json['createdAt']}') ?? DateTime.now(),
        emergency: json['emergency'] == true,
        latitude: json['latitude'] is num ? (json['latitude'] as num).toDouble() : null,
        longitude: json['longitude'] is num ? (json['longitude'] as num).toDouble() : null,
      );
}

class CallRecord {
  CallRecord({
    required this.id,
    required this.peerName,
    required this.video,
    required this.startedAt,
    required this.outgoing,
  });

  final String id;
  final String peerName;
  final bool video;
  final DateTime startedAt;
  final bool outgoing;

  Map<String, dynamic> toJson() => {
        'id': id,
        'peerName': peerName,
        'video': video,
        'startedAt': startedAt.toIso8601String(),
        'outgoing': outgoing,
      };

  factory CallRecord.fromJson(Map<String, dynamic> json) => CallRecord(
        id: '${json['id']}',
        peerName: '${json['peerName']}',
        video: json['video'] == true,
        startedAt: DateTime.tryParse('${json['startedAt']}') ?? DateTime.now(),
        outgoing: json['outgoing'] == true,
      );
}
