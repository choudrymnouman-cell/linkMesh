const linkMeshQrPrefix = 'linkmesh:v2:';

String buildLinkMeshQrPayload(String secret) => '$linkMeshQrPrefix${secret.trim()}';

String? parseLinkMeshQrPayload(String raw) {
  final value = raw.trim();
  if (!value.startsWith(linkMeshQrPrefix)) return null;
  final secret = value.substring(linkMeshQrPrefix.length).trim();
  final valid = RegExp(r'^\d{6}$').hasMatch(secret) || RegExp(r'^[a-fA-F0-9]{64}$').hasMatch(secret);
  return valid ? secret : null;
}
