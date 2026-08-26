import 'package:flutter_test/flutter_test.dart';
import 'package:linkmesh_offline_chat/services/qr_pairing.dart';

void main() {
  test('six digit pairing payload round trips', () {
    expect(parseLinkMeshQrPayload(buildLinkMeshQrPayload('123456')), '123456');
  });

  test('strong pairing payload round trips', () {
    final secret = List.filled(64, 'a').join();
    expect(parseLinkMeshQrPayload(buildLinkMeshQrPayload(secret)), secret);
  });

  test('device QR includes identity for direct pairing', () {
    final data = parseLinkMeshQrData(buildLinkMeshQrPayload('123456', deviceId: 'phone-1', deviceName: 'Ali'));
    expect(data?.secret, '123456');
    expect(data?.deviceId, 'phone-1');
    expect(data?.deviceName, 'Ali');
    expect(data?.hasDevice, isTrue);
  });

  test('foreign and malformed QR values are rejected', () {
    expect(parseLinkMeshQrPayload('https://example.com'), isNull);
    expect(parseLinkMeshQrPayload('linkmesh:v2:123'), isNull);
  });
}
