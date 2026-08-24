import 'package:flutter/material.dart';
import 'app_state.dart';
import 'screens/app_screens.dart';

void main() { WidgetsFlutterBinding.ensureInitialized(); runApp(const LinkMeshBootstrap()); }

class LinkMeshBootstrap extends StatefulWidget { const LinkMeshBootstrap({super.key}); @override State<LinkMeshBootstrap> createState() => _LinkMeshBootstrapState(); }
class _LinkMeshBootstrapState extends State<LinkMeshBootstrap> {
  final state = AppState();
  @override void initState() { super.initState(); state.initialize(); }
  @override void dispose() { state.dispose(); super.dispose(); }
  @override Widget build(BuildContext context) => AnimatedBuilder(animation: state, builder: (_, __) => MaterialApp(
    debugShowCheckedModeBanner: false, title: 'LinkMesh', themeMode: state.darkMode ? ThemeMode.dark : ThemeMode.light,
    theme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF2B64F6)), useMaterial3: true, scaffoldBackgroundColor: const Color(0xFFF6F8FC)),
    darkTheme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF76A0FF), brightness: Brightness.dark), useMaterial3: true),
    home: !state.initialized ? const Scaffold(body: Center(child: CircularProgressIndicator())) : state.onboarded ? MainShell(state: state) : OnboardingScreen(state: state),
  ));
}
