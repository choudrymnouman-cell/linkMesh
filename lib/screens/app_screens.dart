import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

import '../app_state.dart';
import '../models/models.dart';

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

  static const data = [
    ('Stay Connected. Anywhere.', 'No Internet, no cloud account. Talk to nearby LinkMesh devices.', Icons.hub_rounded),
    ('Local-first Messaging', 'Discover phones on the same Wi-Fi or hotspot and exchange messages directly.', Icons.wifi_tethering_rounded),
    ('Emergency Ready', 'Broadcast SOS and community updates to nearby nodes.', Icons.sos_rounded),
  ];

  @override
  void dispose() {
    controller.dispose();
    codeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final item = data[page];
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              const Spacer(),
              Icon(item.$3, size: 88, color: _blue),
              const SizedBox(height: 24),
              Text(item.$1, textAlign: TextAlign.center, style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w800)),
              const SizedBox(height: 12),
              Text(item.$2, textAlign: TextAlign.center),
              const Spacer(),
              if (page < 2)
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: () => setState(() => page++),
                    child: const Padding(padding: EdgeInsets.all(14), child: Text('Continue')),
                  ),
                )
              else ...[
                TextField(controller: controller, decoration: const InputDecoration(labelText: 'Display name', border: OutlineInputBorder())),
                const SizedBox(height: 12),
                TextField(controller: codeController, obscureText: true, decoration: const InputDecoration(labelText: 'Private mesh key', helperText: 'Enter six digits or generate a stronger key', border: OutlineInputBorder())),
                TextButton.icon(onPressed: () { codeController.text = widget.state.generateStrongMeshSecret(); setState(() {}); }, icon: const Icon(Icons.auto_awesome), label: const Text('Generate stronger private key')),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: () {
                      if (!widget.state.validMeshSecret(codeController.text)) {
                        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Enter six digits or generate a strong private key.')));
                        return;
                      }
                      widget.state.setProfile(controller.text, codeController.text);
                    },
                    child: const Padding(padding: EdgeInsets.all(14), child: Text('START LINKMESH')),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
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
      SettingsScreen(state: widget.state),
    ];
    const titles = ['LinkMesh', 'Nearby', 'Chats', 'Groups', 'Settings'];
    return Scaffold(
      appBar: AppBar(
        title: Text(titles[index]),
        actions: [
          IconButton(
            tooltip: 'Restart mesh network',
            onPressed: widget.state.restartNetwork,
            icon: Icon(widget.state.networkRunning ? Icons.wifi : Icons.wifi_off, color: widget.state.networkRunning ? Colors.green : null),
          ),
        ],
      ),
      body: IndexedStack(index: index, children: screens),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() => index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home), label: 'Home'),
          NavigationDestination(icon: Icon(Icons.radar), label: 'Nearby'),
          NavigationDestination(icon: Icon(Icons.chat), label: 'Chats'),
          NavigationDestination(icon: Icon(Icons.groups), label: 'Groups'),
          NavigationDestination(icon: Icon(Icons.settings), label: 'Settings'),
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
          decoration: BoxDecoration(color: _blue, borderRadius: BorderRadius.circular(24)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('LOCAL OFFLINE NETWORK', style: TextStyle(color: Colors.white70, fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              Text('Hello, ${state.username}', style: const TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.bold)),
              Text('$online nearby • ${state.messages.length} saved messages', style: const TextStyle(color: Colors.white70)),
            ],
          ),
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
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: [
            _Action(Icons.radar, 'Discover', () => navigate(1)),
            _Action(Icons.chat, 'Chats', () => navigate(2)),
            _Action(Icons.groups, 'Groups', () => navigate(3)),
            _Action(Icons.sos, 'SOS', () => Navigator.push(context, MaterialPageRoute(builder: (_) => EmergencyScreen(state: state)))),
            _Action(Icons.campaign, 'Community', () => Navigator.push(context, MaterialPageRoute(builder: (_) => CommunityScreen(state: state)))),
            _Action(Icons.monitor_heart, 'Diagnostics', () => Navigator.push(context, MaterialPageRoute(builder: (_) => DiagnosticsScreen(state: state)))),
          ],
        ),
        const SizedBox(height: 18),
        const Text('Recent community alerts', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
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
        width: (MediaQuery.sizeOf(context).width - 44) / 2,
        child: Card(
          child: InkWell(
            onTap: onTap,
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Column(children: [Icon(icon, color: _blue, size: 32), const SizedBox(height: 7), Text(title, style: const TextStyle(fontWeight: FontWeight.bold))]),
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
              FilledButton.icon(onPressed: state.restartNetwork, icon: const Icon(Icons.refresh), label: const Text('Refresh discovery')),
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
  ChatMessage? replyingTo;
  bool recording = false;

  @override
  void dispose() {
    input.dispose();
    unawaited(recorder.dispose());
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
                      return _MessageBubble(message: message, replyMessage: reply, retry: () => widget.state.retryMessage(message), onLongPress: () => _messageActions(message));
                    }).toList(),
                  ),
                ),
                if (replyingTo != null) Material(color: Theme.of(context).colorScheme.surfaceContainerHighest, child: ListTile(dense: true, leading: const Icon(Icons.reply), title: Text('Replying to ${replyingTo!.sender}'), subtitle: Text(replyingTo!.text, maxLines: 1, overflow: TextOverflow.ellipsis), trailing: IconButton(onPressed: () => setState(() => replyingTo = null), icon: const Icon(Icons.close)))),
                _Composer(controller: input, hint: 'Message ${widget.peer.name}', send: _send, attach: () => widget.state.pickAndSendAttachment(widget.peer), voice: _toggleVoice, recording: recording),
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
      final path = await recorder.stop();
      if (mounted) setState(() => recording = false);
      if (path != null) {
        final file = File(path);
        if (await file.exists()) await widget.state.sendAttachment(widget.peer, name: path.split(Platform.pathSeparator).last, bytes: await file.readAsBytes(), localPath: path, mime: 'audio/mp4');
      }
      return;
    }
    if (!await recorder.hasPermission()) return;
    final directory = await getTemporaryDirectory();
    final path = '${directory.path}/voice_${DateTime.now().millisecondsSinceEpoch}.m4a';
    await recorder.start(const RecordConfig(encoder: AudioEncoder.aacLc), path: path);
    if (mounted) setState(() => recording = true);
  }

  void _call(bool video) {
    widget.state.addCall(widget.peer, video);
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(video ? 'Video call' : 'Voice call'),
        content: const Text('Call signaling/media transport is not available on the local Dart mesh yet. The attempt is saved in call history.'),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Close'))],
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message, required this.replyMessage, required this.retry, required this.onLongPress});
  final ChatMessage message;
  final ChatMessage? replyMessage;
  final VoidCallback retry;
  final VoidCallback onLongPress;

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
    return Align(
      alignment: message.mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Card(
        child: InkWell(
          onTap: message.mine && message.status == DeliveryStatus.failed && message.attachmentPath == null ? retry : message.attachmentPath != null ? () => OpenFilex.open(message.attachmentPath!) : null,
          onLongPress: onLongPress,
          child: Padding(
          padding: const EdgeInsets.all(10),
          child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            if (replyMessage != null) Container(width: 220, padding: const EdgeInsets.all(7), margin: const EdgeInsets.only(bottom: 6), decoration: BoxDecoration(color: Theme.of(context).colorScheme.surfaceContainerHighest, borderRadius: BorderRadius.circular(7)), child: Text('${replyMessage!.sender}: ${replyMessage!.text}', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12))),
            Text(message.text),
            if (message.attachmentName != null) Container(margin: const EdgeInsets.only(top: 6), padding: const EdgeInsets.all(8), decoration: BoxDecoration(border: Border.all(color: Theme.of(context).colorScheme.outlineVariant), borderRadius: BorderRadius.circular(8)), child: Row(mainAxisSize: MainAxisSize.min, children: [Icon(message.attachmentMime?.startsWith('audio/') == true ? Icons.mic : message.attachmentMime?.startsWith('image/') == true ? Icons.image : Icons.attach_file), const SizedBox(width: 6), Flexible(child: Text('${message.attachmentName} • ${_fileSize(message.attachmentSize)}', overflow: TextOverflow.ellipsis))])),
            const SizedBox(height: 3),
            Row(mainAxisSize: MainAxisSize.min, children: [if (message.reactions.isNotEmpty) Text(message.reactions.values.join(' ')), const SizedBox(width: 6), Text(_clock(message.sentAt), style: Theme.of(context).textTheme.labelSmall), if (statusIcon != null) ...[const SizedBox(width: 4), Icon(statusIcon, size: 14, color: statusColor)]]),
          ]),
          ),
        ),
      ),
    );
  }
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
        builder: (_, __) => ListView(
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
            ListTile(leading: const Icon(Icons.monitor_heart), title: const Text('Diagnostics'), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => DiagnosticsScreen(state: state)))),
            ListTile(leading: const Icon(Icons.delete_forever, color: Colors.red), title: const Text('Clear local messages and history'), onTap: state.clearLocalData),
            const ListTile(leading: Icon(Icons.info_outline), title: Text('LinkMesh'), subtitle: Text('Flutter recreation • local-first messaging')),
          ],
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
    final payload = 'linkmesh:v2:${state.meshCode}';
    return Scaffold(
      appBar: AppBar(title: const Text('Secure QR pairing')),
      body: ListView(padding: const EdgeInsets.all(24), children: [
        const Text('Show this QR code only to a trusted person. It contains the private key for your encrypted local network.', textAlign: TextAlign.center),
        const SizedBox(height: 20),
        Center(child: ColoredBox(color: Colors.white, child: Padding(padding: const EdgeInsets.all(12), child: QrImageView(data: payload, size: 240)))),
        const SizedBox(height: 20),
        FilledButton.icon(onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => QrScannerScreen(state: state))), icon: const Icon(Icons.qr_code_scanner), label: const Text('Scan another LinkMesh QR')),
        OutlinedButton.icon(onPressed: () async { await state.updateMeshCode(state.generateStrongMeshSecret()); if (context.mounted) Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => QrPairingScreen(state: state))); }, icon: const Icon(Icons.refresh), label: const Text('Generate a new secure network key')),
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
  @override Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Scan trusted device')), body: MobileScanner(onDetect: (capture) async {
    if (handled || capture.barcodes.isEmpty) return;
    final raw = capture.barcodes.first.rawValue ?? '';
    if (!raw.startsWith('linkmesh:v2:')) return;
    final secret = raw.substring('linkmesh:v2:'.length);
    if (!widget.state.validMeshSecret(secret)) return;
    handled = true;
    await widget.state.updateMeshCode(secret);
    if (context.mounted) Navigator.pop(context);
  }));
}

class CallHistoryScreen extends StatelessWidget {
  const CallHistoryScreen({super.key, required this.state});
  final AppState state;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Call history')),
        body: state.calls.isEmpty
            ? const _Empty('No call attempts', 'Voice/video transport is not implemented yet.')
            : ListView(children: state.calls.map((record) => ListTile(leading: Icon(record.video ? Icons.videocam : Icons.call), title: Text(record.peerName), subtitle: Text(_time(record.startedAt)))).toList()),
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
