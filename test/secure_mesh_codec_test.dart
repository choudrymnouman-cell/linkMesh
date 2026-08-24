import 'package:flutter_test/flutter_test.dart';
import 'package:linkmesh_offline_chat/services/secure_mesh_codec.dart';

void main() {
  test('same mesh code decrypts an authenticated packet', () async {
    final encrypted = await SecureMeshCodec('123456').encrypt({'type': 'message', 'text': 'hello'});
    expect(await SecureMeshCodec('123456').decrypt(encrypted), {'type': 'message', 'text': 'hello'});
  });

  test('different mesh code cannot decrypt packet', () async {
    final encrypted = await SecureMeshCodec('123456').encrypt({'text': 'secret'});
    expect(await SecureMeshCodec('654321').decrypt(encrypted), isNull);
  });

  test('tampered packet is rejected', () async {
    final codec = SecureMeshCodec('123456');
    final encrypted = await codec.encrypt({'text': 'secret'});
    expect(await codec.decrypt('${encrypted}x'), isNull);
  });
}
