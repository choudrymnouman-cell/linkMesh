import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:just_audio/just_audio.dart';

import '../app_state.dart';
import '../models/models.dart';
import '../services/qr_pairing.dart';

const _blue = Color(0xFF2B64F6);

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key, required this.state});
  final AppState state;

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final controller = TextEditingController();
  final codeController = TextEditingController();
  int page = 0;

  @override
  void dispose() {
    controller.dispose();
    codeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: AnimatedSwitcher(duration: const Duration(milliseconds: 250), child: page == 0 ? _welcome(context) : _profile(context)),
      ),
    );
  }

  Widget _welcome(BuildContext context) => ListView(
        key: const ValueKey('welcome'),
        padding: const EdgeInsets.fromLTRB(24, 30, 24, 24),
        children: [
          Row(children: [ClipRRect(borderRadius: BorderRadius.circular(14), child: Image.asset('assets/images/linkmesh_icon.png', width: 48, height: 48)), const SizedBox(width: 12), const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('LINKMESH', style: TextStyle(fontSize: 23, fontWeight: FontWeight.w900, letterSpacing: 1)), Text('Stay connected. Anywhere.', style: TextStyle(color: Colors.black54))])]),
          const SizedBox(height: 42),
          Container(
            height: 260,
            decoration: BoxDecoration(gradient: const LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [Color(0xFF071A3D), Color(0xFF0A3F78)]), borderRadius: BorderRadius.circular(30)),
            child: Stack(alignment: Alignment.center, children: [
              ...List.generate(3, (index) => Container(width: 90.0 + index * 55, height: 90.0 + index * 55, decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: const Color(0xFF39D9E8).withValues(alpha: .23))))),
              const Icon(Icons.hub_rounded, size: 110, color: Color(0xFF43E5DD)),
              const Positioned(left: 32, top: 38, child: _MeshNode(icon: Icons.phone_android)),
              const Positioned(right: 32, top: 58, child: _MeshNode(icon: Icons.smartphone)),
              const Positioned(left: 48, bottom: 34, child: _MeshNode(icon: Icons.tablet_android)),
              const Positioned(right: 54, bottom: 28, child: _MeshNode(icon: Icons.phone_iphone)),
            ]),
          ),
          const SizedBox(height: 30),
          const Text('Offline communication.\nLimitless connection.', style: TextStyle(fontSize: 30, height: 1.15, fontWeight: FontWeight.w900)),
          const SizedBox(height: 12),
          const Text('Create a secure nearby network for messages, voice and video calls, groups, files and emergency alerts — without the Internet.', style: TextStyle(height: 1.5, color: Colors.black54)),
          const SizedBox(height: 28),
          FilledButton.icon(onPressed: () { codeController.text = widget.state.generateStrongMeshSecret(); setState(() => page = 1); }, icon: const Icon(Icons.add_circle_outline), label: const Padding(padding: EdgeInsets.symmetric(vertical: 15), child: Text('CREATE PRIVATE NETWORK'))),
          const SizedBox(height: 10),
          OutlinedButton.icon(onPressed: _scanToJoin, icon: const Icon(Icons.qr_code_scanner), label: const Padding(padding: EdgeInsets.symmetric(vertical: 15), child: Text('JOIN WITH QR CODE'))),
          TextButton(onPressed: () => setState(() => page = 1), child: const Text('Join with a 6-digit code')),
          const Row(mainAxisAlignment: MainAxisAlignment.center, children: [Icon(Icons.lock_outline, size: 16, color: Colors.green), SizedBox(width: 6), Text('Private • encrypted • local only', style: TextStyle(fontSize: 12, color: Colors.black54))]),
        ],
      );

  Widget _profile(BuildContext context) => ListView(
        key: const ValueKey('profile'),
        padding: const EdgeInsets.all(24),
        children: [
          Align(alignment: Alignment.centerLeft, child: IconButton(onPressed: () => setState(() => page = 0), icon: const Icon(Icons.arrow_back))),
          const SizedBox(height: 28),
          const CircleAvatar(radius: 44, backgroundColor: Color(0xFFDDEBFF), child: Icon(Icons.person_rounded, size: 48, color: _blue)),
          const SizedBox(height: 24),
          const Text('Set up your profile', textAlign: TextAlign.center, style: TextStyle(fontSize: 28, fontWeight: FontWeight.w900)),
          const SizedBox(height: 8),
          const Text('Nearby people will see this name. No phone number or account is required.', textAlign: TextAlign.center, style: TextStyle(color: Colors.black54)),
          const SizedBox(height: 30),
          TextField(controller: controller, textCapitalization: TextCapitalization.words, decoration: const InputDecoration(labelText: 'Display name', prefixIcon: Icon(Icons.person_outline))),
          const SizedBox(height: 14),
          TextField(controller: codeController, obscureText: true, keyboardType: TextInputType.number, onChanged: (_) => setState(() {}), decoration: const InputDecoration(labelText: 'Private network key', helperText: 'Use the same key on trusted phones', prefixIcon: Icon(Icons.key_rounded))),
          const SizedBox(height: 10),
          if (widget.state.validMeshSecret(codeController.text)) const ListTile(contentPadding: EdgeInsets.zero, leading: Icon(Icons.verified_user_rounded, color: Colors.green), title: Text('Secure network key ready'), subtitle: Text('You can share it later using QR pairing.')),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: () {
              if (!widget.state.validMeshSecret(codeController.text)) { ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Enter a 6-digit network code or go back and scan a LinkMesh QR.'))); return; }
              widget.state.setProfile(controller.text, codeController.text);
            },
            child: const Padding(padding: EdgeInsets.all(15), child: Text('START LINKMESH')),
          ),
        ],
      );

  Future<void> _scanToJoin() async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => QrScannerScreen(state: widget.state)));
    if (!mounted || !widget.state.validMeshSecret(widget.state.meshCode)) return;
    codeController.text = widget.state.meshCode;
    setState(() => page = 1);
  }
}

class _MeshNode extends StatelessWidget {
  const _MeshNode({required this.icon});
  final IconData icon;
  @override Widget build(BuildContext context) => Container(width: 46, height: 46, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(14), boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 10)]), child: Icon(icon, color: _blue));
}

class MainShell extends StatefulWidget {
  const MainShell({super.key, required this.state});
  final AppState state;

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int index = 0;

  @override
  Widget build(BuildContext context) {
    final screens = <Widget>[
      DashboardScreen(state: widget.state, navigate: (value) => setState(() => index = value)),
      NearbyScreen(state: widget.state),
      ChatsScreen(state: widget.state),
      GroupsScreen(state: widget.state),
      FilesScreen(state: widget.state),
    ];
    const titles = ['Dashboard', 'People Nearby', 'Chats', 'Groups', 'Files'];
    return Scaffold(
      appBar: AppBar(
        title: Row(children: [ClipRRect(borderRadius: BorderRadius.circular(9), child: Image.asset('assets/images/linkmesh_icon.png', width: 34, height: 34)), const SizedBox(width: 10), Expanded(child: Text(titles[index], style: const TextStyle(fontWeight: FontWeight.w800)))]),
        actions: [
          IconButton(tooltip: 'QR pairing', onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => QrPairingScreen(state: widget.state))), icon: const Icon(Icons.qr_code_2_rounded)),
          IconButton(
            tooltip: 'Restart mesh network',
            onPressed: widget.state.restartNetwork,
            icon: Icon(widget.state.networkRunning ? Icons.wifi : Icons.wifi_off, color: widget.state.networkRunning ? Colors.green : null),
          ),
          IconButton(tooltip: 'Settings', onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => SettingsScreen(state: widget.state))), icon: const Icon(Icons.settings_outlined)),
        ],
      ),
      body: IndexedStack(index: index, children: screens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() => index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.dashboard_rounded), label: 'Home'),
          NavigationDestination(icon: Icon(Icons.radar), label: 'Nearby'),
          NavigationDestination(icon: Icon(Icons.chat), label: 'Chats'),
          NavigationDestination(icon: Icon(Icons.groups), label: 'Groups'),
          NavigationDestination(icon: Icon(Icons.folder_rounded), label: 'Files'),
        ],
      ),
    );
  }
}

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key, required this.state, required this.navigate});
  final AppState state;
  final ValueChanged<int> navigate;

  @override
  Widget build(BuildContext context) {
    final online = state.peers.where((peer) => peer.online && !peer.blocked).length;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(gradient: const LinearGradient(colors: [Color(0xFF154BC6), Color(0xFF2B64F6), Color(0xFF25A9E8)]), borderRadius: BorderRadius.circular(24)),
          child: Row(children: [
            ClipRRect(borderRadius: BorderRadius.circular(18), child: Image.asset('assets/images/linkmesh_icon.png', width: 68, height: 68)),
            const SizedBox(width: 16),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('MESH NETWORK', style: TextStyle(color: Colors.white70, fontWeight: FontWeight.bold)),
              const SizedBox(height: 6),
              Text(state.networkRunning ? 'Connected' : 'Paused', style: const TextStyle(color: Colors.white, fontSize: 25, fontWeight: FontWeight.bold)),
              Text('$online devices online • ${state.messages.length} messages', style: const TextStyle(color: Colors.white70)),
            ])),
          ]),
        ),
        if (state.networkError != null)
          Card(
            color: Theme.of(context).colorScheme.errorContainer,
            child: ListTile(
              leading: const Icon(Icons.error_outline),
              title: const Text('Mesh could not start'),
              subtitle: Text(state.networkError!),
              trailing: TextButton(onPressed: state.openSystemSettings, child: const Text('Settings')),
            ),
          ),
        const SizedBox(height: 12),
        const Text('Quick actions', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        GridView.count(
          crossAxisCount: 3,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 8,
          crossAxisSpacing: 8,
          childAspectRatio: 1.05,
          children: [
            _Action(Icons.chat_bubble_rounded, 'Chats', () => navigate(2)),
            _Action(Icons.call_rounded, 'Calls', () => Navigator.push(context, MaterialPageRoute(builder: (_) => CallHistoryScreen(state: state)))),
            _Action(Icons.people_alt_rounded, 'People', () => navigate(1)),
            _Action(Icons.folder_rounded, 'Files', () => navigate(4)),
            _Action(Icons.qr_code_scanner_rounded, 'Pair QR', () => Navigator.push(context, MaterialPageRoute(builder: (_) => QrPairingScreen(state: state)))),
            _Action(Icons.sos_rounded, 'SOS', () => Navigator.push(context, MaterialPageRoute(builder: (_) => EmergencyScreen(state: state)))),
          ],
        ),
        const SizedBox(height: 18),
        Row(children: [const Expanded(child: Text('Community feed', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold))), TextButton(onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CommunityScreen(state: state))), child: const Text('View all'))]),
        ...state.posts.take(4).map(
              (post) => Card(
                child: ListTile(
                  leading: Icon(post.emergency ? Icons.warning : Icons.campaign, color: post.emergency ? Colors.red : _blue),
                  title: Text(post.author),
                  subtitle: Text(post.text),
                ),
              ),
            ),
      ],
    );
  }
}

class _Action extends StatelessWidget {
  const _Action(this.icon, this.title, this.onTap);
  final IconData icon;
  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => SizedBox(
        child: Card(
          margin: EdgeInsets.zero,
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [Icon(icon, color: _blue, size: 28), const SizedBox(height: 7), Text(title, style: const TextStyle(fontWeight: FontWeight.bold))]),
            ),
          ),
        ),
      );
}

class NearbyScreen extends StatelessWidget {
  const NearbyScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: state,
        builder: (_, __) {
          final peers = [...state.peers]..sort((a, b) => (b.favorite ? 1 : 0).compareTo(a.favorite ? 1 : 0));
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Card(child: ListTile(leading: const CircleAvatar(backgroundColor: Color(0xFFE7EFFF), child: Icon(Icons.radar, color: _blue)), title: Text('${peers.where((peer) => peer.online).length} devices found'), subtitle: const Text('Nearby phones appear automatically while LinkMesh is open'))),
              if (peers.isEmpty) const _Empty('No nearby nodes', 'Put two phones on the same Wi-Fi/hotspot and open LinkMesh.'),
              ...peers.map(
                (peer) => Card(
                  child: ListTile(
                    leading: CircleAvatar(child: Icon(peer.blocked ? Icons.block : Icons.person)),
                    title: Row(children: [Expanded(child: Text(peer.name)), if (peer.favorite) const Icon(Icons.star, color: Colors.amber, size: 18)]),
                    subtitle: Text(peer.blocked ? 'Blocked' : peer.online ? 'Online • ${peer.host}' : 'Last seen ${_time(peer.lastSeen)}'),
                    trailing: PopupMenuButton<String>(
                      onSelected: (value) {
                        if (value == 'favorite') state.toggleFavorite(peer);
                        if (value == 'block') state.toggleBlocked(peer);
                      },
                      itemBuilder: (_) => [
                        PopupMenuItem(value: 'favorite', child: Text(peer.favorite ? 'Remove favorite' : 'Favorite')),
                        PopupMenuItem(value: 'block', child: Text(peer.blocked ? 'Unblock' : 'Block')),
                      ],
                    ),
                    onTap: peer.blocked ? null : () => Navigator.push(context, MaterialPageRoute(builder: (_) => ChatScreen(state: state, peer: peer))),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Row(children: [Expanded(child: OutlinedButton.icon(onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => QrScannerScreen(state: state))), icon: const Icon(Icons.qr_code_scanner), label: const Text('SCAN QR'))), const SizedBox(width: 10), Expanded(child: FilledButton.icon(onPressed: state.restartNetwork, icon: const Icon(Icons.refresh), label: const Text('REFRESH')))]),
            ],
          );
        },
      );
}

class ChatsScreen extends StatelessWidget {
  const ChatsScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: state,
        builder: (_, __) {
          final ids = state.messages.where((message) => message.groupId == null).map((message) => message.peerId).toSet();
          final peers = state.peers.where((peer) => ids.contains(peer.id) || peer.online).toList();
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (peers.isEmpty) const _Empty('No chats yet', 'Discover a nearby device to begin.'),
              ...peers.map((peer) {
                final peerMessages = state.messages.where((message) => message.peerId == peer.id && message.groupId == null).toList();
                return Card(
                  child: ListTile(
                    leading: CircleAvatar(child: Text(peer.name.isEmpty ? '?' : peer.name[0].toUpperCase())),
                    title: Text(peer.name),
                    subtitle: Text(peerMessages.isEmpty ? 'Start local chat' : peerMessages.last.text, maxLines: 1, overflow: TextOverflow.ellipsis),
                    trailing: Icon(peer.online ? Icons.circle : Icons.circle_outlined, size: 12, color: peer.online ? Colors.green : Colors.grey),
                    onTap: peer.blocked ? null : () => Navigator.push(context, MaterialPageRoute(builder: (_) => ChatScreen(state: state, peer: peer))),
                  ),
                );
              }),
            ],
          );
        },
      );
}

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key, required this.state, required this.peer});
  final AppState state;
  final MeshPeer peer;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final input = TextEditingController();
  final recorder = AudioRecorder();
  final audioPlayer = AudioPlayer();
  StreamSubscription<PlayerState>? audioSubscription;
  ChatMessage? replyingTo;
  String? activeVoiceNoteId;
  bool recording = false;

  @override
  void initState() {
    super.initState();
    audioSubscription = audioPlayer.playerStateStream.listen((playerState) {
      if (playerState.processingState == ProcessingState.completed && mounted) {
        setState(() => activeVoiceNoteId = null);
      }
    });
  }

  @override
  void dispose() {
    input.dispose();
    unawaited(recorder.dispose());
    unawaited(audioSubscription?.cancel());
    unawaited(audioPlayer.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: widget.state,
        builder: (_, __) {
          final messages = widget.state.messages.where((message) => message.peerId == widget.peer.id && message.groupId == null).toList();
          return Scaffold(
            appBar: AppBar(
              title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(widget.peer.name), Text(widget.peer.online ? 'Online' : 'Offline', style: const TextStyle(fontSize: 11))]),
              actions: [
                IconButton(onPressed: () => _search(messages), icon: const Icon(Icons.search)),
                IconButton(onPressed: () => _call(false), icon: const Icon(Icons.call)),
                IconButton(onPressed: () => _call(true), icon: const Icon(Icons.videocam)),
              ],
            ),
            body: Column(
              children: [
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.all(12),
                    children: messages.map((message) {
                      final reply = message.replyToId == null ? null : widget.state.messages.where((m) => m.id == message.replyToId).firstOrNull;
                      return _MessageBubble(
                        message: message,
                        replyMessage: reply,
                        retry: () => widget.state.retryMessage(message),
                        onLongPress: () => _messageActions(message),
                        audioPlayer: audioPlayer,
                        voiceNoteActive: activeVoiceNoteId == message.id,
                        playVoiceNote: () => _playVoiceNote(message),
                      );
                    }).toList(),
                  ),
                ),
                if (replyingTo != null) Material(color: Theme.of(context).colorScheme.surfaceContainerHighest, child: ListTile(dense: true, leading: const Icon(Icons.reply), title: Text('Replying to ${replyingTo!.sender}'), subtitle: Text(replyingTo!.text, maxLines: 1, overflow: TextOverflow.ellipsis), trailing: IconButton(onPressed: () => setState(() => replyingTo = null), icon: const Icon(Icons.close)))),
                _Composer(controller: input, hint: recording ? 'Recording voice note… tap stop to send' : 'Message ${widget.peer.name}', send: _send, attach: () => widget.state.pickAndSendAttachment(widget.peer), voice: _toggleVoice, recording: recording),
              ],
            ),
          );
        },
      );

  void _send() {
    final text = input.text;
    input.clear();
    final replyId = replyingTo?.id;
    setState(() => replyingTo = null);
    widget.state.sendMessage(widget.peer, text, replyToId: replyId);
  }

  void _search(List<ChatMessage> messages) {
    showSearch<ChatMessage?>(context: context, delegate: _MessageSearchDelegate(messages));
  }

  void _messageActions(ChatMessage message) {
    showModalBottomSheet<void>(context: context, builder: (sheetContext) => SafeArea(child: Wrap(children: [
      ListTile(leading: const Icon(Icons.reply), title: const Text('Reply'), onTap: () { Navigator.pop(sheetContext); setState(() => replyingTo = message); }),
      ListTile(leading: const Icon(Icons.copy), title: const Text('Copy'), onTap: () { Clipboard.setData(ClipboardData(text: message.text)); Navigator.pop(sheetContext); }),
      ListTile(leading: const Text('👍', style: TextStyle(fontSize: 24)), title: const Text('React'), onTap: () { widget.state.reactToMessage(widget.peer, message, '👍'); Navigator.pop(sheetContext); }),
      ListTile(leading: const Icon(Icons.delete_outline, color: Colors.red), title: const Text('Delete from this phone'), onTap: () { widget.state.deleteMessage(message); Navigator.pop(sheetContext); }),
    ])));
  }

  Future<void> _toggleVoice() async {
    if (recording) {
      try {
        final path = await recorder.stop();
        if (mounted) setState(() => recording = false);
        if (path != null) {
          final file = File(path);
          if (await file.exists()) {
            final sent = await widget.state.sendAttachment(widget.peer, name: path.split(Platform.pathSeparator).last, bytes: await file.readAsBytes(), localPath: path, mime: 'audio/mp4');
            if (!sent && mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Voice note could not be delivered. Keep it under 5 MB and check that the peer is online.')));
          }
        }
      } catch (_) {
        if (mounted) { setState(() => recording = false); ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Voice recording could not be completed.'))); }
      }
      return;
    }
    if (!await recorder.hasPermission()) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Allow microphone access to record a voice note.'))); return; }
    try {
      final directory = await getTemporaryDirectory();
      final path = '${directory.path}/voice_${DateTime.now().millisecondsSinceEpoch}.m4a';
      await recorder.start(const RecordConfig(encoder: AudioEncoder.aacLc), path: path);
      if (mounted) setState(() => recording = true);
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Voice recording is unavailable on this device.')));
    }
  }

  Future<void> _playVoiceNote(ChatMessage message) async {
    final path = message.attachmentPath;
    if (path == null) return;
    if (activeVoiceNoteId == message.id) {
      if (audioPlayer.playing) {
        await audioPlayer.pause();
      } else {
        await audioPlayer.play();
      }
      if (mounted) setState(() {});
      return;
    }
    try {
      await audioPlayer.stop();
      await audioPlayer.setFilePath(path);
      if (mounted) setState(() => activeVoiceNoteId = message.id);
      await audioPlayer.play();
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('This voice note could not be played.')));
    }
  }

  Future<void> _call(bool video) async {
    final started = await widget.state.startCall(widget.peer, video);
    if (!started && mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(widget.state.callError ?? 'Could not start the call. Check permissions and try again.')));
  }
}

class CallScreen extends StatelessWidget {
  const CallScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) {
    final call = state.callService;
    final status = call.error ?? (call.incoming ? 'Incoming ${call.video ? 'video' : 'audio'} call' : call.connected ? 'Secure local call' : 'Connecting over LinkMesh…');
    return PopScope(
      canPop: false,
      child: Scaffold(
        backgroundColor: const Color(0xFF101522),
        body: SafeArea(child: Stack(children: [
          if (call.video && !call.incoming)
            Positioned.fill(child: RTCVideoView(call.remoteRenderer, objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover))
          else
            Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
              CircleAvatar(radius: 56, backgroundColor: _blue, child: Text(_initial(call.peerName), style: const TextStyle(color: Colors.white, fontSize: 42, fontWeight: FontWeight.bold))),
              const SizedBox(height: 24),
              Text(call.peerName ?? 'Nearby peer', style: const TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text(status, style: TextStyle(color: call.error == null ? Colors.white70 : Colors.redAccent)),
              if (call.connected) StreamBuilder<int>(stream: Stream.periodic(const Duration(seconds: 1), (value) => value), builder: (_, __) => Padding(padding: const EdgeInsets.only(top: 8), child: Text(_duration(DateTime.now().difference(call.connectedAt ?? DateTime.now())), style: const TextStyle(color: Colors.white, fontSize: 18)))),
            ])),
          if (call.video && !call.incoming) Positioned(top: 18, right: 18, width: 110, height: 160, child: ClipRRect(borderRadius: BorderRadius.circular(16), child: RTCVideoView(call.localRenderer, mirror: true, objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover))),
          Positioned(left: 20, right: 20, bottom: 32, child: call.incoming
              ? Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [FloatingActionButton(backgroundColor: Colors.red, heroTag: 'reject', onPressed: call.reject, child: const Icon(Icons.call_end)), FloatingActionButton(backgroundColor: Colors.green, heroTag: 'accept', onPressed: state.acceptCall, child: const Icon(Icons.call))])
              : Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
                  FloatingActionButton(heroTag: 'mute', onPressed: call.toggleMute, child: Icon(call.muted ? Icons.mic_off : Icons.mic)),
                  if (call.video)
                    FloatingActionButton(heroTag: 'camera', onPressed: call.toggleCamera, child: Icon(call.cameraEnabled ? Icons.videocam : Icons.videocam_off))
                  else
                    FloatingActionButton(heroTag: 'speaker', onPressed: call.toggleSpeaker, child: Icon(call.speakerOn ? Icons.volume_up : Icons.hearing)),
                  if (call.video)
                    FloatingActionButton(heroTag: 'switch', onPressed: call.switchCamera, child: const Icon(Icons.cameraswitch))
                  else
                    FloatingActionButton(heroTag: 'message', onPressed: () => _showInCallChat(context), child: const Icon(Icons.chat_bubble)),
                  FloatingActionButton(backgroundColor: Colors.red, foregroundColor: Colors.white, heroTag: 'hangup', onPressed: call.end, child: const Icon(Icons.call_end)),
                ])),
        ])),
      ),
    );
  }

  void _showInCallChat(BuildContext context) {
    final peerId = state.callService.peerId;
    if (peerId == null) return;
    final peer = state.peers.where((value) => value.id == peerId).firstOrNull;
    if (peer == null) return;
    showModalBottomSheet<void>(context: context, isScrollControlled: true, builder: (_) => _InCallChatSheet(state: state, peer: peer));
  }
}

class _InCallChatSheet extends StatefulWidget {
  const _InCallChatSheet({required this.state, required this.peer});
  final AppState state;
  final MeshPeer peer;
  @override State<_InCallChatSheet> createState() => _InCallChatSheetState();
}

class _InCallChatSheetState extends State<_InCallChatSheet> {
  final input = TextEditingController();
  @override void dispose() { input.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: widget.state,
        builder: (_, __) {
          final messages = widget.state.messages.where((message) => message.peerId == widget.peer.id && message.groupId == null).toList();
          return SafeArea(
            child: Padding(
              padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
              child: SizedBox(
                height: MediaQuery.sizeOf(context).height * .68,
                child: Column(children: [
                  ListTile(leading: const Icon(Icons.lock, color: _blue), title: Text('Chat with ${widget.peer.name}'), subtitle: const Text('The call continues while you message'), trailing: IconButton(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.close))),
                  const Divider(height: 1),
                  Expanded(child: messages.isEmpty ? const _Empty('No messages yet', 'Send a message without leaving the call.') : ListView(padding: const EdgeInsets.all(12), children: messages.map((message) => Align(alignment: message.mine ? Alignment.centerRight : Alignment.centerLeft, child: Card(color: message.mine ? Theme.of(context).colorScheme.primaryContainer : null, child: Padding(padding: const EdgeInsets.all(10), child: Text(message.text))))).toList())),
                  _Composer(controller: input, hint: 'Message during call', send: () { final text = input.text; input.clear(); widget.state.sendMessage(widget.peer, text); }),
                ]),
              ),
            ),
          );
        },
      );
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message, required this.replyMessage, required this.retry, required this.onLongPress, required this.audioPlayer, required this.voiceNoteActive, required this.playVoiceNote});
  final ChatMessage message;
  final ChatMessage? replyMessage;
  final VoidCallback retry;
  final VoidCallback onLongPress;
  final AudioPlayer audioPlayer;
  final bool voiceNoteActive;
  final VoidCallback playVoiceNote;

  @override
  Widget build(BuildContext context) {
    IconData? statusIcon;
    Color? statusColor;
    if (message.mine) {
      switch (message.status) {
        case DeliveryStatus.pending:
          statusIcon = Icons.schedule;
        case DeliveryStatus.delivered:
          statusIcon = Icons.done;
        case DeliveryStatus.failed:
          statusIcon = Icons.error_outline;
          statusColor = Colors.red;
      }
    }
    final isAudio = message.attachmentMime?.startsWith('audio/') == true;
    return Align(
      alignment: message.mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Card(
        child: InkWell(
          onTap: message.mine && message.status == DeliveryStatus.failed && message.attachmentPath == null ? retry : message.attachmentPath != null && !isAudio ? () => OpenFilex.open(message.attachmentPath!) : null,
          onLongPress: onLongPress,
          child: Padding(
          padding: const EdgeInsets.all(10),
          child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            if (replyMessage != null) Container(width: 220, padding: const EdgeInsets.all(7), margin: const EdgeInsets.only(bottom: 6), decoration: BoxDecoration(color: Theme.of(context).colorScheme.surfaceContainerHighest, borderRadius: BorderRadius.circular(7)), child: Text('${replyMessage!.sender}: ${replyMessage!.text}', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12))),
            if (!isAudio) Text(message.text),
            if (isAudio)
              _InlineVoiceNote(player: audioPlayer, active: voiceNoteActive, onPlay: playVoiceNote)
            else if (message.attachmentName != null)
              Container(margin: const EdgeInsets.only(top: 6), padding: const EdgeInsets.all(8), decoration: BoxDecoration(border: Border.all(color: Theme.of(context).colorScheme.outlineVariant), borderRadius: BorderRadius.circular(8)), child: Row(mainAxisSize: MainAxisSize.min, children: [Icon(message.attachmentMime?.startsWith('image/') == true ? Icons.image : Icons.attach_file), const SizedBox(width: 6), Flexible(child: Text('${message.attachmentName} • ${_fileSize(message.attachmentSize)}', overflow: TextOverflow.ellipsis))])),
            const SizedBox(height: 3),
            Row(mainAxisSize: MainAxisSize.min, children: [if (message.reactions.isNotEmpty) Text(message.reactions.values.join(' ')), const SizedBox(width: 6), Text(_clock(message.sentAt), style: Theme.of(context).textTheme.labelSmall), if (statusIcon != null) ...[const SizedBox(width: 4), Icon(statusIcon, size: 14, color: statusColor)]]),
          ]),
          ),
        ),
      ),
    );
  }
}

class _InlineVoiceNote extends StatelessWidget {
  const _InlineVoiceNote({required this.player, required this.active, required this.onPlay});
  final AudioPlayer player;
  final bool active;
  final VoidCallback onPlay;

  @override
  Widget build(BuildContext context) => SizedBox(
        width: 230,
        child: Row(
          children: [
            StreamBuilder<PlayerState>(
              stream: active ? player.playerStateStream : null,
              builder: (_, snapshot) {
                final playing = active && (snapshot.data?.playing ?? player.playing);
                return IconButton.filledTonal(onPressed: onPlay, icon: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded));
              },
            ),
            const SizedBox(width: 8),
            Expanded(
              child: StreamBuilder<Duration>(
                stream: active ? player.positionStream : null,
                builder: (_, positionSnapshot) => StreamBuilder<Duration?>(
                  stream: active ? player.durationStream : null,
                  builder: (_, durationSnapshot) {
                    final position = active ? positionSnapshot.data ?? Duration.zero : Duration.zero;
                    final duration = active ? durationSnapshot.data ?? Duration.zero : Duration.zero;
                    final progress = duration.inMilliseconds == 0 ? 0.0 : (position.inMilliseconds / duration.inMilliseconds).clamp(0.0, 1.0).toDouble();
                    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      LinearProgressIndicator(value: progress, minHeight: 4, borderRadius: BorderRadius.circular(4)),
                      const SizedBox(height: 5),
                      Text(active ? '${_duration(position)} / ${_duration(duration)}' : 'Voice note', style: Theme.of(context).textTheme.labelSmall),
                    ]);
                  },
                ),
              ),
            ),
            const SizedBox(width: 8),
            const Icon(Icons.graphic_eq_rounded, color: _blue),
          ],
        ),
      );
}

class GroupsScreen extends StatelessWidget {
  const GroupsScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: state,
        builder: (_, __) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            FilledButton.icon(onPressed: () => _create(context), icon: const Icon(Icons.add), label: const Text('Create private group')),
            const SizedBox(height: 10),
            ...state.groups.map(
              (group) => Card(
                child: ListTile(
                  leading: const Icon(Icons.groups),
                  title: Text(group.name),
                  subtitle: Text('${group.description}${group.isPrivate ? ' • ${group.members.length} private members' : ' • public'}'),
                  trailing: state.canManageGroup(group) ? IconButton(icon: const Icon(Icons.admin_panel_settings), onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => GroupAdminScreen(state: state, group: group)))) : null,
                  onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => GroupChatScreen(state: state, group: group))),
                ),
              ),
            ),
          ],
        ),
      );

  void _create(BuildContext context) {
    final name = TextEditingController();
    final description = TextEditingController();
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Create group'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [TextField(controller: name, decoration: const InputDecoration(labelText: 'Name')), TextField(controller: description, decoration: const InputDecoration(labelText: 'Description'))],
        ),
        actions: [
          FilledButton(
            onPressed: () {
              state.createGroup(name.text, description.text);
              Navigator.pop(context);
            },
            child: const Text('Create'),
          ),
        ],
      ),
    ).whenComplete(() {
      name.dispose();
      description.dispose();
    });
  }
}

class FilesScreen extends StatelessWidget {
  const FilesScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: state,
        builder: (_, __) {
          final attachments = state.messages.where((message) => message.attachmentPath != null).toList().reversed.toList();
          final received = attachments.where((message) => !message.mine).toList();
          final sent = attachments.where((message) => message.mine).toList();
          return DefaultTabController(
            length: 2,
            child: Column(
              children: [
                const TabBar(tabs: [Tab(text: 'Received'), Tab(text: 'Sent')]),
                Expanded(child: TabBarView(children: [_FileList(state: state, messages: received), _FileList(state: state, messages: sent)])),
                SafeArea(top: false, child: Padding(padding: const EdgeInsets.fromLTRB(16, 8, 16, 12), child: SizedBox(width: double.infinity, child: FilledButton.icon(onPressed: () => _choosePeerForFile(context), icon: const Icon(Icons.send_rounded), label: const Text('SEND A FILE'))))),
              ],
            ),
          );
        },
      );

  void _choosePeerForFile(BuildContext context) {
    final peers = state.peers.where((peer) => peer.online && !peer.blocked).toList();
    if (peers.isEmpty) { ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('No nearby online device is available.'))); return; }
    showModalBottomSheet<void>(context: context, builder: (sheetContext) => SafeArea(child: Column(mainAxisSize: MainAxisSize.min, children: [
      const ListTile(title: Text('Send file to', style: TextStyle(fontWeight: FontWeight.bold))),
      ...peers.map((peer) => ListTile(leading: CircleAvatar(child: Text(_initial(peer.name))), title: Text(peer.name), subtitle: const Text('Online'), onTap: () { Navigator.pop(sheetContext); state.pickAndSendAttachment(peer); })),
    ])));
  }
}

class _FileList extends StatelessWidget {
  const _FileList({required this.state, required this.messages});
  final AppState state;
  final List<ChatMessage> messages;

  @override
  Widget build(BuildContext context) {
    if (messages.isEmpty) return const _Empty('No shared files', 'Files and voice notes shared in chats will appear here.');
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: messages.length,
      separatorBuilder: (_, __) => const SizedBox(height: 6),
      itemBuilder: (_, index) {
        final message = messages[index];
        final peer = state.peers.where((value) => value.id == message.peerId).firstOrNull;
        final isAudio = message.attachmentMime?.startsWith('audio/') == true;
        final isImage = message.attachmentMime?.startsWith('image/') == true;
        return Card(
          child: ListTile(
            leading: CircleAvatar(backgroundColor: const Color(0xFFE7EFFF), child: Icon(isAudio ? Icons.graphic_eq_rounded : isImage ? Icons.image_rounded : Icons.insert_drive_file_rounded, color: _blue)),
            title: Text(isAudio ? 'Voice note' : message.attachmentName ?? 'Shared file', maxLines: 1, overflow: TextOverflow.ellipsis),
            subtitle: Text('${message.mine ? 'Sent to' : 'From'} ${peer?.name ?? message.sender} • ${_fileSize(message.attachmentSize)}\n${_time(message.sentAt)}'),
            isThreeLine: true,
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              if (isAudio && peer != null) {
                Navigator.push(context, MaterialPageRoute(builder: (_) => ChatScreen(state: state, peer: peer)));
              } else {
                OpenFilex.open(message.attachmentPath!);
              }
            },
          ),
        );
      },
    );
  }
}

class GroupAdminScreen extends StatelessWidget {
  const GroupAdminScreen({super.key, required this.state, required this.group});
  final AppState state;
  final MeshGroup group;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(animation: state, builder: (_, __) {
      final available = state.peers.where((peer) => !peer.blocked && !group.members.contains(peer.id)).toList();
      return Scaffold(
      appBar: AppBar(title: Text('${group.name} administration')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        const Text('Members', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
        ...group.members.map((id) {
          final peer = state.peers.where((p) => p.id == id).firstOrNull;
          final name = id == state.deviceId ? '${state.username} (you)' : peer?.name ?? id.substring(0, id.length < 8 ? id.length : 8);
          final owner = id == group.ownerId;
          final admin = group.adminIds.contains(id);
          return Card(child: ListTile(title: Text(name), subtitle: Text(owner ? 'Owner' : admin ? 'Administrator' : 'Member'), trailing: id == state.deviceId || owner ? null : PopupMenuButton<String>(onSelected: (value) { if (value == 'admin') state.toggleGroupAdmin(group, id); if (value == 'remove') state.removeGroupMember(group, id); }, itemBuilder: (_) => [PopupMenuItem(value: 'admin', child: Text(admin ? 'Remove administrator' : 'Make administrator')), const PopupMenuItem(value: 'remove', child: Text('Remove member'))])));
        }),
        const SizedBox(height: 16),
        const Text('Invite nearby device', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
        if (available.isEmpty) const ListTile(title: Text('No available nearby devices')),
        ...available.map((peer) => ListTile(leading: const Icon(Icons.person_add), title: Text(peer.name), subtitle: Text(peer.online ? 'Online' : 'Offline'), enabled: peer.online, onTap: peer.online ? () => state.addGroupMember(group, peer) : null)),
      ]),
      );
    });
  }
}

class GroupChatScreen extends StatefulWidget {
  const GroupChatScreen({super.key, required this.state, required this.group});
  final AppState state;
  final MeshGroup group;

  @override
  State<GroupChatScreen> createState() => _GroupChatScreenState();
}

class _GroupChatScreenState extends State<GroupChatScreen> {
  final input = TextEditingController();

  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: widget.state,
        builder: (_, __) {
          final messages = widget.state.messages.where((message) => message.groupId == widget.group.id).toList();
          return Scaffold(
            appBar: AppBar(title: Text(widget.group.name)),
            body: Column(
              children: [
                Expanded(
                  child: messages.isEmpty
                      ? const _Empty('No group messages', 'Messages are broadcast to nearby LinkMesh nodes.')
                      : ListView(
                          padding: const EdgeInsets.all(12),
                          children: messages.map((message) => Card(child: ListTile(title: Text(message.sender), subtitle: Text(message.text)))).toList(),
                        ),
                ),
                _Composer(
                  controller: input,
                  hint: 'Group message',
                  send: () {
                    final text = input.text;
                    input.clear();
                    widget.state.sendGroupMessage(widget.group, text);
                  },
                ),
              ],
            ),
          );
        },
      );
}

class CommunityScreen extends StatefulWidget {
  const CommunityScreen({super.key, required this.state});
  final AppState state;

  @override
  State<CommunityScreen> createState() => _CommunityScreenState();
}

class _CommunityScreenState extends State<CommunityScreen> {
  final input = TextEditingController();

  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: widget.state,
        builder: (_, __) => Scaffold(
          appBar: AppBar(title: const Text('Community broadcasts')),
          body: Column(
            children: [
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(12),
                  children: widget.state.posts.map((post) => Card(child: ListTile(leading: Icon(post.emergency ? Icons.warning : Icons.campaign, color: post.emergency ? Colors.red : _blue), title: Text(post.author), subtitle: Text(post.text)))).toList(),
                ),
              ),
              _Composer(
                controller: input,
                hint: 'Broadcast nearby update',
                send: () {
                  final text = input.text;
                  input.clear();
                  widget.state.postCommunity(text);
                },
              ),
            ],
          ),
        ),
      );
}

class EmergencyScreen extends StatelessWidget {
  const EmergencyScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: state,
        builder: (_, __) => Scaffold(
          appBar: AppBar(title: const Text('Emergency Center')),
          body: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.sos, size: 100, color: state.sosActive ? Colors.red : Colors.orange),
                Text(state.sosActive ? 'SOS ACTIVE' : 'SOS Beacon', style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
                const SizedBox(height: 10),
                const Text('Sends an emergency broadcast to nearby LinkMesh devices. This is not a replacement for official emergency services.', textAlign: TextAlign.center),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    style: FilledButton.styleFrom(backgroundColor: state.sosActive ? Colors.grey : Colors.red),
                    onPressed: state.sosActive ? state.stopSos : state.triggerSos,
                    child: Padding(padding: const EdgeInsets.all(16), child: Text(state.sosActive ? 'STOP SOS' : 'BROADCAST SOS')),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
}

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: state,
        builder: (_, __) => Scaffold(
          appBar: AppBar(title: const Text('Settings', style: TextStyle(fontWeight: FontWeight.w800))),
          body: ListView(
            padding: const EdgeInsets.all(16),
            children: [
            Card(
              child: ListTile(
                leading: const CircleAvatar(child: Icon(Icons.person)),
                title: Text(state.username),
                subtitle: Text('Node ${state.deviceId.substring(0, 8)}'),
                trailing: IconButton(icon: const Icon(Icons.edit), onPressed: () => _edit(context)),
              ),
            ),
            SwitchListTile(value: state.darkMode, onChanged: state.toggleTheme, secondary: const Icon(Icons.dark_mode), title: const Text('Dark mode')),
            ListTile(leading: const Icon(Icons.lock), title: const Text('Private mesh code'), subtitle: const Text('AES-256 encrypted trusted network'), trailing: const Icon(Icons.edit), onTap: () => _editMeshCode(context)),
            ListTile(leading: const Icon(Icons.qr_code_2), title: const Text('QR secure pairing'), subtitle: const Text('Connect a trusted phone without typing the key'), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => QrPairingScreen(state: state)))),
            ListTile(leading: const Icon(Icons.backup), title: const Text('Create encrypted backup'), subtitle: const Text('Protected by the current private mesh key'), onTap: () => _backup(context)),
            ListTile(leading: const Icon(Icons.restore), title: const Text('Restore encrypted backup'), subtitle: const Text('Requires the same private mesh key'), onTap: () => _restore(context)),
            SwitchListTile(
              value: state.networkRunning,
              onChanged: (value) => value ? state.startNetwork() : state.stopNetwork(),
              secondary: const Icon(Icons.wifi),
              title: const Text('Local mesh network'),
              subtitle: Text(state.networkRunning ? 'Discovery active' : 'Discovery stopped'),
            ),
            ListTile(leading: const Icon(Icons.history), title: const Text('Call history'), subtitle: Text('${state.calls.length} attempts'), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CallHistoryScreen(state: state)))),
            if (Platform.isAndroid) ListTile(leading: const Icon(Icons.device_hub), title: const Text('Bluetooth & Wi-Fi Direct'), subtitle: Text(state.p2p.status), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => P2pTransportScreen(state: state)))),
            ListTile(leading: const Icon(Icons.monitor_heart), title: const Text('Diagnostics'), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => DiagnosticsScreen(state: state)))),
            ListTile(leading: const Icon(Icons.delete_forever, color: Colors.red), title: const Text('Clear local messages and history'), onTap: state.clearLocalData),
            const ListTile(leading: Icon(Icons.info_outline), title: Text('LinkMesh'), subtitle: Text('Flutter recreation • local-first messaging')),
            ],
          ),
        ),
      );

  void _edit(BuildContext context) {
    final controller = TextEditingController(text: state.username);
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Edit profile'),
        content: TextField(controller: controller),
        actions: [
          FilledButton(
            onPressed: () {
              state.updateProfile(controller.text);
              Navigator.pop(context);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    ).whenComplete(controller.dispose);
  }

  void _editMeshCode(BuildContext context) {
    final controller = TextEditingController();
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Change private mesh code'),
        content: TextField(controller: controller, keyboardType: TextInputType.number, maxLength: 6, obscureText: true, decoration: const InputDecoration(helperText: 'All trusted phones must use the same code')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(onPressed: () { if (RegExp(r'^\d{6}$').hasMatch(controller.text.trim())) { state.updateMeshCode(controller.text); Navigator.pop(context); } }, child: const Text('Save')),
        ],
      ),
    ).whenComplete(controller.dispose);
  }

  Future<void> _backup(BuildContext context) async {
    final ok = await state.exportEncryptedBackup();
    if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ok ? 'Encrypted backup saved.' : 'Backup cancelled.')));
  }

  Future<void> _restore(BuildContext context) async {
    final ok = await state.restoreEncryptedBackup();
    if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ok ? 'Backup restored successfully.' : 'Restore failed or was cancelled.')));
  }
}

class QrPairingScreen extends StatelessWidget {
  const QrPairingScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) {
    final payload = buildLinkMeshQrPayload(state.meshCode);
    return Scaffold(
      appBar: AppBar(title: const Text('QR pairing', style: TextStyle(fontWeight: FontWeight.w800))),
      body: ListView(padding: const EdgeInsets.all(24), children: [
        const Icon(Icons.verified_user_rounded, color: _blue, size: 42),
        const SizedBox(height: 10),
        const Text('Connect a trusted phone', textAlign: TextAlign.center, style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900)),
        const SizedBox(height: 8),
        const Text('Open LinkMesh on the other phone and scan this code. Both phones will join the same encrypted local network.', textAlign: TextAlign.center),
        const SizedBox(height: 22),
        Center(child: Container(padding: const EdgeInsets.all(18), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(24), boxShadow: const [BoxShadow(color: Color(0x1A146EF5), blurRadius: 24)]), child: QrImageView(data: payload, size: 238))),
        const SizedBox(height: 18),
        const Card(child: Padding(padding: EdgeInsets.all(14), child: Row(children: [Icon(Icons.lock_outline, color: Colors.green), SizedBox(width: 10), Expanded(child: Text('Only share this QR with people you trust. It contains your private network key.'))]))),
        const SizedBox(height: 12),
        FilledButton.icon(onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => QrScannerScreen(state: state))), icon: const Icon(Icons.qr_code_scanner), label: const Padding(padding: EdgeInsets.symmetric(vertical: 13), child: Text('SCAN A LINKMESH QR'))),
        OutlinedButton.icon(onPressed: () async { await state.updateMeshCode(state.generateStrongMeshSecret()); if (context.mounted) Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => QrPairingScreen(state: state))); }, icon: const Icon(Icons.refresh), label: const Text('Create a new private network key')),
      ]),
    );
  }
}

class QrScannerScreen extends StatefulWidget {
  const QrScannerScreen({super.key, required this.state});
  final AppState state;
  @override State<QrScannerScreen> createState() => _QrScannerScreenState();
}

class _QrScannerScreenState extends State<QrScannerScreen> {
  bool handled = false;
  bool invalidCodeSeen = false;

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: Colors.black,
        appBar: AppBar(title: const Text('Scan trusted device')),
        body: Stack(
          fit: StackFit.expand,
          children: [
            MobileScanner(onDetect: _detect),
            IgnorePointer(child: Center(child: Container(width: 260, height: 260, decoration: BoxDecoration(border: Border.all(color: invalidCodeSeen ? Colors.orange : const Color(0xFF43E5DD), width: 4), borderRadius: BorderRadius.circular(28))))),
            Align(
              alignment: Alignment.bottomCenter,
              child: SafeArea(
                child: Container(
                  width: double.infinity,
                  margin: const EdgeInsets.all(20),
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(color: Colors.black.withValues(alpha: .75), borderRadius: BorderRadius.circular(18)),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text(invalidCodeSeen ? 'This is not a LinkMesh pairing QR.' : 'Place the LinkMesh QR inside the frame', textAlign: TextAlign.center, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 6),
                    const Text('The network key is saved locally and pairing finishes automatically.', textAlign: TextAlign.center, style: TextStyle(color: Colors.white70, fontSize: 12)),
                    TextButton(onPressed: _enterCode, child: const Text('Enter 6-digit code instead')),
                  ]),
                ),
              ),
            ),
          ],
        ),
      );

  Future<void> _detect(BarcodeCapture capture) async {
    if (handled || capture.barcodes.isEmpty) return;
    final secret = parseLinkMeshQrPayload(capture.barcodes.first.rawValue ?? '');
    if (secret == null) { if (!invalidCodeSeen && mounted) setState(() => invalidCodeSeen = true); return; }
    handled = true;
    await widget.state.updateMeshCode(secret);
    if (mounted) Navigator.pop(context, true);
  }

  void _enterCode() {
    final controller = TextEditingController();
    showDialog<void>(context: context, builder: (dialogContext) => AlertDialog(title: const Text('Join private network'), content: TextField(controller: controller, autofocus: true, obscureText: true, keyboardType: TextInputType.number, maxLength: 6, decoration: const InputDecoration(labelText: '6-digit network code')), actions: [TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Cancel')), FilledButton(onPressed: () async { final secret = controller.text.trim(); if (!widget.state.validMeshSecret(secret)) return; handled = true; await widget.state.updateMeshCode(secret); if (dialogContext.mounted) Navigator.pop(dialogContext); if (mounted) Navigator.pop(context, true); }, child: const Text('Join'))])).whenComplete(controller.dispose);
  }
}

class CallHistoryScreen extends StatelessWidget {
  const CallHistoryScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Call history')),
        body: state.calls.isEmpty
            ? const _Empty('No calls yet', 'Encrypted nearby voice and video calls will appear here.')
            : ListView(children: state.calls.map((record) => ListTile(leading: Icon(record.video ? Icons.videocam : Icons.call), title: Text(record.peerName), subtitle: Text(_time(record.startedAt)))).toList()),
      );
}

class P2pTransportScreen extends StatelessWidget {
  const P2pTransportScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: state.p2p,
    builder: (_, __) => Scaffold(
      appBar: AppBar(title: const Text('Bluetooth & Wi-Fi Direct')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Card(child: ListTile(leading: const Icon(Icons.info_outline), title: Text(state.p2p.status), subtitle: const Text('Bluetooth LE discovers a nearby host; Wi-Fi Direct carries encrypted LinkMesh traffic without a router.'))),
        const SizedBox(height: 12),
        FilledButton.icon(onPressed: state.createP2pGroup, icon: const Icon(Icons.wifi_tethering), label: const Text('Create nearby mesh group')),
        const SizedBox(height: 8),
        OutlinedButton.icon(onPressed: state.p2p.discover, icon: const Icon(Icons.bluetooth_searching), label: Text(state.p2p.scanning ? 'Scanning…' : 'Find nearby mesh groups')),
        if (state.p2p.discoveredHosts.isNotEmpty) ...[
          const SizedBox(height: 16),
          const Text('Available groups', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
          ...state.p2p.discoveredHosts.map((device) => Card(child: ListTile(leading: const Icon(Icons.router), title: Text(device.toString()), trailing: const Icon(Icons.link), onTap: () => state.connectP2pHost(device)))),
        ],
        const SizedBox(height: 8),
        TextButton.icon(onPressed: state.disconnectP2p, icon: const Icon(Icons.link_off), label: const Text('Disconnect Wi-Fi Direct')),
        const Padding(padding: EdgeInsets.only(top: 16), child: Text('Android may ask for Nearby devices, Bluetooth, Wi-Fi, and Location services. LinkMesh does not upload discovered-device data.')),
      ]),
    ),
  );
}

class DiagnosticsScreen extends StatelessWidget {
  const DiagnosticsScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Mesh diagnostics')),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _diag('Network', state.networkRunning ? 'Running' : 'Stopped'),
            _diag('Node ID', state.deviceId),
            _diag('Nearby peers', '${state.peers.where((peer) => peer.online).length} online / ${state.peers.length} known'),
            _diag('Saved messages', '${state.messages.length}'),
            _diag('Groups', '${state.groups.length}'),
            _diag('Community posts', '${state.posts.length}'),
            _diag('Discovery port', '40444 / UDP'),
            _diag('Message port', '40445 / TCP'),
            if (state.networkError != null) _diag('Last network error', state.networkError!),
          ],
        ),
      );

  Widget _diag(String title, String value) => Card(child: ListTile(title: Text(title), subtitle: Text(value)));
}

class _Composer extends StatelessWidget {
  const _Composer({required this.controller, required this.hint, required this.send, this.attach, this.voice, this.recording = false});
  final TextEditingController controller;
  final String hint;
  final VoidCallback send;
  final VoidCallback? attach;
  final VoidCallback? voice;
  final bool recording;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(10),
        child: Row(
          children: [
            if (attach != null) IconButton(onPressed: attach, icon: const Icon(Icons.attach_file)),
            if (voice != null) IconButton(onPressed: voice, icon: Icon(recording ? Icons.stop_circle : Icons.mic, color: recording ? Colors.red : null)),
            Expanded(child: TextField(controller: controller, onSubmitted: (_) => send(), decoration: InputDecoration(hintText: hint, border: const OutlineInputBorder()))),
            const SizedBox(width: 8),
            IconButton.filled(onPressed: send, icon: const Icon(Icons.send)),
          ],
        ),
      );
}

class _Empty extends StatelessWidget {
  const _Empty(this.title, this.subtitle);
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(40),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.portable_wifi_off, size: 54),
              const SizedBox(height: 12),
              Text(title, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.bold)),
              Text(subtitle, textAlign: TextAlign.center),
            ],
          ),
        ),
      );
}

class _MessageSearchDelegate extends SearchDelegate<ChatMessage?> {
  _MessageSearchDelegate(this.messages);
  final List<ChatMessage> messages;

  @override List<Widget>? buildActions(BuildContext context) => [if (query.isNotEmpty) IconButton(onPressed: () => query = '', icon: const Icon(Icons.clear))];
  @override Widget? buildLeading(BuildContext context) => IconButton(onPressed: () => close(context, null), icon: const Icon(Icons.arrow_back));
  @override Widget buildSuggestions(BuildContext context) => _results();
  @override Widget buildResults(BuildContext context) => _results();

  Widget _results() {
    final clean = query.trim().toLowerCase();
    final found = clean.isEmpty ? messages.reversed.take(20) : messages.reversed.where((m) => m.text.toLowerCase().contains(clean) || m.sender.toLowerCase().contains(clean));
    return ListView(children: found.map((m) => ListTile(leading: Icon(m.mine ? Icons.north_east : Icons.south_west), title: Text(m.text, maxLines: 2, overflow: TextOverflow.ellipsis), subtitle: Text('${m.sender} • ${_time(m.sentAt)}'))).toList());
  }
}

String _clock(DateTime date) => '${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
String _initial(String? name) { final clean = name?.trim() ?? ''; return clean.isEmpty ? '?' : clean[0].toUpperCase(); }
String _duration(Duration value) => '${value.inMinutes.toString().padLeft(2, '0')}:${(value.inSeconds % 60).toString().padLeft(2, '0')}';
String _fileSize(int? bytes) {
  if (bytes == null) return '';
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
}

String _time(DateTime? date) {
  if (date == null) return 'never';
  final difference = DateTime.now().difference(date);
  if (difference.inSeconds < 60) return '${difference.inSeconds}s ago';
  if (difference.inMinutes < 60) return '${difference.inMinutes}m ago';
  if (difference.inHours < 24) return '${difference.inHours}h ago';
  return '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
}
