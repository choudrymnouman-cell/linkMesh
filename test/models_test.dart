import 'package:flutter_test/flutter_test.dart';
import 'package:linkmesh_offline_chat/models/models.dart';

void main() {
  test('chat message survives JSON round trip', () {
    final original = ChatMessage(id: '1', peerId: 'peer', sender: 'A', text: 'hello', sentAt: DateTime(2026, 1, 1), mine: true, status: DeliveryStatus.failed, replyToId: '0', reactions: {'peer': '👍'}, attachmentName: 'photo.jpg', attachmentPath: '/tmp/photo.jpg', attachmentMime: 'image/jpeg', attachmentSize: 2048);
    final restored = ChatMessage.fromJson(original.toJson());
    expect(restored.id, '1');
    expect(restored.text, 'hello');
    expect(restored.status, DeliveryStatus.failed);
    expect(restored.replyToId, '0');
    expect(restored.reactions, {'peer': '👍'});
    expect(restored.attachmentName, 'photo.jpg');
    expect(restored.attachmentMime, 'image/jpeg');
    expect(restored.attachmentSize, 2048);
  });

  test('peer preferences survive JSON round trip', () {
    final restored = MeshPeer.fromJson(MeshPeer(id: 'p', name: 'Peer', host: '192.168.1.2', favorite: true, blocked: true).toJson());
    expect(restored.favorite, isTrue);
    expect(restored.blocked, isTrue);
  });
}
