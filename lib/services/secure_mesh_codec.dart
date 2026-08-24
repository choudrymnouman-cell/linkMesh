import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart' as crypto;
import 'package:cryptography/cryptography.dart';

class SecureMeshCodec {
  SecureMeshCodec(String meshCode)
      : _key = SecretKey(crypto.sha256.convert(utf8.encode('linkmesh-v2:$meshCode')).bytes);

  final SecretKey _key;
  final AesGcm _cipher = AesGcm.with256bits();
  final Random _random = Random.secure();

  Future<String> encrypt(Map<String, dynamic> value) async {
    final nonce = Uint8List.fromList(List<int>.generate(12, (_) => _random.nextInt(256)));
    final box = await _cipher.encrypt(utf8.encode(jsonEncode(value)), secretKey: _key, nonce: nonce);
    return jsonEncode({'v': 2, 'n': base64UrlEncode(box.nonce), 'c': base64UrlEncode(box.cipherText), 'm': base64UrlEncode(box.mac.bytes)});
  }

  Future<Map<String, dynamic>?> decrypt(String encoded) async {
    try {
      final envelope = Map<String, dynamic>.from(jsonDecode(encoded) as Map);
      if (envelope['v'] != 2) return null;
      final clear = await _cipher.decrypt(
        SecretBox(base64Url.decode('${envelope['c']}'), nonce: base64Url.decode('${envelope['n']}'), mac: Mac(base64Url.decode('${envelope['m']}'))),
        secretKey: _key,
      );
      return Map<String, dynamic>.from(jsonDecode(utf8.decode(clear)) as Map);
    } on Object {
      return null;
    }
  }
}
