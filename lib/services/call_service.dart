import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

typedef CallSignalSender = Future<void> Function(String peerId, Map<String, dynamic> signal);

class CallService extends ChangeNotifier {
  CallService(this._sendSignal);
  final CallSignalSender _sendSignal;
  final RTCVideoRenderer localRenderer = RTCVideoRenderer();
  final RTCVideoRenderer remoteRenderer = RTCVideoRenderer();
  RTCPeerConnection? _connection;
  MediaStream? _localStream;
  Timer? _ringTimer;
  final List<RTCIceCandidate> _pendingCandidates = [];
  String? peerId;
  String? peerName;
  String? callId;
  bool video = false;
  bool incoming = false;
  bool connected = false;
  bool muted = false;
  bool cameraEnabled = true;
  bool speakerOn = false;
  DateTime? connectedAt;
  String? error;
  Map<String, dynamic>? _pendingOffer;
  bool _initialized = false;
  Future<void>? _initialization;

  bool get active => callId != null;

  Future<void> initialize() async {
    if (_initialized) return;
    final pending = _initialization;
    if (pending != null) return pending;
    final future = _initializeRenderers();
    _initialization = future;
    try {
      await future;
    } catch (_) {
      _initialization = null;
      rethrow;
    }
  }

  Future<void> _initializeRenderers() async { await localRenderer.initialize(); await remoteRenderer.initialize(); _initialized = true; }

  Future<void> start({required String targetId, required String targetName, required bool withVideo, required String id}) async {
    if (active) return;
    peerId = targetId; peerName = targetName; video = withVideo; callId = id; incoming = false; connected = false; connectedAt = null; error = null; muted = false; cameraEnabled = true; speakerOn = withVideo;
    notifyListeners();
    try {
      await initialize();
      await _prepareConnection();
      final offer = await _connection!.createOffer();
      await _connection!.setLocalDescription(offer);
      await _sendSignal(targetId, {'kind': 'offer', 'callId': id, 'video': withVideo, 'sdp': offer.sdp, 'sdpType': offer.type});
      _armRingTimeout();
      notifyListeners();
    } catch (cause) {
      error = 'Could not start ${withVideo ? 'video' : 'audio'} call';
      await end(notifyPeer: false, clearError: false);
      throw StateError('$error: $cause');
    }
  }

  Future<void> receive(String fromId, String fromName, Map<String, dynamic> signal) async {
    final kind = signal['kind']?.toString();
    final id = signal['callId']?.toString();
    if (kind == 'offer') {
      if (id == null || id.isEmpty) return;
      if (active) { await _sendSignal(fromId, {'kind': 'busy', 'callId': id}); return; }
      peerId = fromId; peerName = fromName; callId = id; video = signal['video'] == true; incoming = true; connected = false; connectedAt = null; error = null; muted = false; cameraEnabled = true; speakerOn = video; _pendingOffer = signal; _armRingTimeout(); notifyListeners(); return;
    }
    if (id == null || id != callId || fromId != peerId) return;
    if (kind == 'answer') {
      await _connection?.setRemoteDescription(RTCSessionDescription(signal['sdp']?.toString(), signal['sdpType']?.toString()));
      await _flushCandidates();
    } else if (kind == 'candidate') {
      final candidate = RTCIceCandidate(signal['candidate']?.toString(), signal['sdpMid']?.toString(), int.tryParse('${signal['sdpMLineIndex']}'));
      if (_connection == null) { _pendingCandidates.add(candidate); } else { await _connection!.addCandidate(candidate); }
    } else if (kind == 'hangup' || kind == 'reject' || kind == 'busy') {
      await end(notifyPeer: false);
    }
  }

  Future<void> accept() async {
    final offer = _pendingOffer; final target = peerId; final id = callId;
    if (offer == null || target == null || id == null) return;
    incoming = false; error = null; notifyListeners();
    try {
      await initialize();
      await _prepareConnection();
      await _connection!.setRemoteDescription(RTCSessionDescription(offer['sdp']?.toString(), offer['sdpType']?.toString()));
      await _flushCandidates();
      final answer = await _connection!.createAnswer();
      await _connection!.setLocalDescription(answer);
      await _sendSignal(target, {'kind': 'answer', 'callId': id, 'sdp': answer.sdp, 'sdpType': answer.type});
      _pendingOffer = null;
      _armRingTimeout();
      notifyListeners();
    } catch (cause) {
      error = 'Could not connect the call';
      await end(notifyPeer: true, clearError: false);
      throw StateError('$error: $cause');
    }
  }

  Future<void> reject() async { final target = peerId; final id = callId; if (target != null && id != null) await _sendSignal(target, {'kind': 'reject', 'callId': id}); await end(notifyPeer: false); }

  Future<void> end({bool notifyPeer = true, bool clearError = true}) async {
    final target = peerId; final id = callId;
    _ringTimer?.cancel(); _ringTimer = null;
    if (notifyPeer && target != null && id != null) { try { await _sendSignal(target, {'kind': 'hangup', 'callId': id}); } catch (_) { /* The peer may already be unreachable. */ } }
    for (final track in _localStream?.getTracks() ?? const <MediaStreamTrack>[]) { try { await track.stop(); } catch (_) { /* Already stopped. */ } }
    try { await _localStream?.dispose(); } catch (_) { /* Already disposed. */ }
    try { await _connection?.close(); } catch (_) { /* Already closed. */ }
    _localStream = null; _connection = null; localRenderer.srcObject = null; remoteRenderer.srcObject = null;
    peerId = null; peerName = null; callId = null; incoming = false; connected = false; connectedAt = null; _pendingOffer = null; _pendingCandidates.clear(); muted = false; cameraEnabled = true; speakerOn = false; if (clearError) error = null;
    notifyListeners();
  }

  void toggleMute() { muted = !muted; for (final track in _localStream?.getAudioTracks() ?? const <MediaStreamTrack>[]) { track.enabled = !muted; } notifyListeners(); }
  void toggleCamera() { cameraEnabled = !cameraEnabled; for (final track in _localStream?.getVideoTracks() ?? const <MediaStreamTrack>[]) { track.enabled = cameraEnabled; } notifyListeners(); }
  Future<void> switchCamera() async { final tracks = _localStream?.getVideoTracks() ?? const <MediaStreamTrack>[]; if (tracks.isNotEmpty) await Helper.switchCamera(tracks.first); }
  Future<void> toggleSpeaker() async { speakerOn = !speakerOn; try { await Helper.setSpeakerphoneOn(speakerOn); } catch (_) { speakerOn = !speakerOn; } notifyListeners(); }

  Future<void> _prepareConnection() async {
    _localStream = await navigator.mediaDevices.getUserMedia({'audio': true, 'video': video ? {'facingMode': 'user'} : false});
    try { await Helper.setSpeakerphoneOn(speakerOn); } catch (_) { /* Keep the system-selected audio route. */ }
    localRenderer.srcObject = _localStream;
    _connection = await createPeerConnection({'iceServers': const [], 'sdpSemantics': 'unified-plan'});
    for (final track in _localStream!.getTracks()) { await _connection!.addTrack(track, _localStream!); }
    _connection!.onIceCandidate = (candidate) { final target = peerId; final id = callId; if (target != null && id != null && candidate.candidate != null) { unawaited(_sendSignal(target, {'kind': 'candidate', 'callId': id, 'candidate': candidate.candidate, 'sdpMid': candidate.sdpMid, 'sdpMLineIndex': candidate.sdpMLineIndex})); } };
    _connection!.onTrack = (event) { if (event.streams.isNotEmpty) remoteRenderer.srcObject = event.streams.first; notifyListeners(); };
    _connection!.onConnectionState = (state) {
      if (state == RTCPeerConnectionState.RTCPeerConnectionStateConnected) {
        connected = true; connectedAt ??= DateTime.now(); error = null; _ringTimer?.cancel(); _ringTimer = null;
      } else if (state == RTCPeerConnectionState.RTCPeerConnectionStateFailed || state == RTCPeerConnectionState.RTCPeerConnectionStateDisconnected) {
        connected = false; error = 'Call connection lost';
      }
      notifyListeners();
    };
  }

  void _armRingTimeout() {
    _ringTimer?.cancel();
    _ringTimer = Timer(const Duration(seconds: 45), () { if (active && !connected) { error = incoming ? 'Missed call' : 'No answer from nearby device'; notifyListeners(); } });
  }

  Future<void> _flushCandidates() async { final connection = _connection; if (connection == null) return; for (final candidate in _pendingCandidates) { await connection.addCandidate(candidate); } _pendingCandidates.clear(); }
}
