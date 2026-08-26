import 'dart:convert';

const linkMeshQrPrefix = 'linkmesh:v2:';
const _linkMeshQrV3Prefix = 'linkmesh:v3:';

class LinkMeshQrData {
  const LinkMeshQrData({required this.secret, this.deviceId = '', this.deviceName = ''});

  final String secret;
  final String deviceId;
  final String deviceName;

  bool get hasDevice => deviceId.trim().isNotEmpty;
}

String buildLinkMeshQrPayload(String secret, {String deviceId = '', String deviceName = ''}) {
  final normalized = secret.trim();
  if (deviceId.trim().isEmpty) return '$linkMeshQrPrefix$normalized';
  final value = jsonEncode({'secret': normalized, 'deviceId': deviceId.trim(), 'deviceName': deviceName.trim()});
  return '$_linkMeshQrV3Prefix${base64Url.encode(utf8.encode(value)).replaceAll('=', '')}';
}

String? parseLinkMeshQrPayload(String raw) {
  return parseLinkMeshQrData(raw)?.secret;
}

LinkMeshQrData? parseLinkMeshQrData(String raw) {
  final value = raw.trim();
  if (value.startsWith(linkMeshQrPrefix)) {
    final secret = value.substring(linkMeshQrPrefix.length).trim();
    final valid = RegExp(r'^\d{6}$').hasMatch(secret) || RegExp(r'^[a-fA-F0-9]{64}$').hasMatch(secret);
    return valid ? LinkMeshQrData(secret: secret) : null;
  }
  if (!value.startsWith(_linkMeshQrV3Prefix)) return null;
  try {
    var encoded = value.substring(_linkMeshQrV3Prefix.length).trim();
    encoded += '=' * ((4 - encoded.length % 4) % 4);
    final decoded = jsonDecode(utf8.decode(base64Url.decode(encoded)));
    if (decoded is! Map) return null;
    final secret = decoded['secret']?.toString().trim() ?? '';
    final valid = RegExp(r'^\d{6}$').hasMatch(secret) || RegExp(r'^[a-fA-F0-9]{64}$').hasMatch(secret);
    if (!valid) return null;
    return LinkMeshQrData(secret: secret, deviceId: decoded['deviceId']?.toString() ?? '', deviceName: decoded['deviceName']?.toString() ?? '');
  } catch (_) {
    return null;
  }
}
