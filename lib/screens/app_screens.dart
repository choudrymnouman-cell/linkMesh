import 'package:flutter/material.dart';

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
  int page = 0;

  static const data = [
    ('Stay Connected. Anywhere.', 'No Internet, no cloud account. Talk to nearby LinkMesh devices.', Icons.hub_rounded),
    ('Local-first Messaging', 'Discover phones on the same Wi-Fi or hotspot and exchange messages directly.', Icons.wifi_tethering_rounded),
    ('Emergency Ready', 'Broadcast SOS and community updates to nearby nodes.', Icons.sos_rounded),
  ];

  @override
  void dispose() {
    controller.dispose();
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
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: () => widget.state.setProfile(controller.text),
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
            child: ListTile(leading: const Icon(Icons.error_outline), title: const Text('Mesh could not start'), subtitle: Text(state.networkError!)),
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

  @override
  void dispose() {
    input.dispose();
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
                IconButton(onPressed: () => _call(false), icon: const Icon(Icons.call)),
                IconButton(onPressed: () => _call(true), icon: const Icon(Icons.videocam)),
              ],
            ),
            body: Column(
              children: [
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.all(12),
                    children: messages.map((message) => _MessageBubble(message: message)).toList(),
                  ),
                ),
                _Composer(controller: input, hint: 'Message ${widget.peer.name}', send: _send),
              ],
            ),
          );
        },
      );

  void _send() {
    final text = input.text;
    input.clear();
    widget.state.sendMessage(widget.peer, text);
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
  const _MessageBubble({required this.message});
  final ChatMessage message;

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
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [Text(message.text), if (statusIcon != null) Icon(statusIcon, size: 14, color: statusColor)]),
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
            FilledButton.icon(onPressed: () => _create(context), icon: const Icon(Icons.add), label: const Text('Create & announce group')),
            const SizedBox(height: 10),
            ...state.groups.map(
              (group) => Card(
                child: ListTile(
                  leading: const Icon(Icons.groups),
                  title: Text(group.name),
                  subtitle: Text(group.description),
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
  const _Composer({required this.controller, required this.hint, required this.send});
  final TextEditingController controller;
  final String hint;
  final VoidCallback send;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(10),
        child: Row(
          children: [
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

String _time(DateTime? date) {
  if (date == null) return 'never';
  final difference = DateTime.now().difference(date);
  if (difference.inSeconds < 60) return '${difference.inSeconds}s ago';
  if (difference.inMinutes < 60) return '${difference.inMinutes}m ago';
  if (difference.inHours < 24) return '${difference.inHours}h ago';
  return '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
}
