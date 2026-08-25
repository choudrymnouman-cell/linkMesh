import 'package:flutter/material.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'app_state.dart';
import 'screens/app_screens.dart';

void main() { WidgetsFlutterBinding.ensureInitialized(); FlutterForegroundTask.initCommunicationPort(); runApp(const LinkMeshBootstrap()); }

class LinkMeshBootstrap extends StatefulWidget { const LinkMeshBootstrap({super.key}); @override State<LinkMeshBootstrap> createState() => _LinkMeshBootstrapState(); }
class _LinkMeshBootstrapState extends State<LinkMeshBootstrap> with WidgetsBindingObserver {
  final state = AppState();
  @override void initState() { super.initState(); WidgetsBinding.instance.addObserver(this); state.initialize(); }
  @override void didChangeAppLifecycleState(AppLifecycleState lifecycle) { if (!state.initialized) return; if (lifecycle == AppLifecycleState.paused) { state.enterBackground(); } else if (lifecycle == AppLifecycleState.resumed) { state.resumeFromBackground(); } }
  @override void dispose() { WidgetsBinding.instance.removeObserver(this); state.dispose(); super.dispose(); }
  @override Widget build(BuildContext context) => AnimatedBuilder(animation: state, builder: (_, __) => MaterialApp(
    debugShowCheckedModeBanner: false, title: 'LinkMesh', themeMode: state.darkMode ? ThemeMode.dark : ThemeMode.light,
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF146EF5), primary: const Color(0xFF146EF5)),
      useMaterial3: true,
      scaffoldBackgroundColor: const Color(0xFFF5F8FD),
      appBarTheme: const AppBarTheme(backgroundColor: Color(0xFFF5F8FD), surfaceTintColor: Colors.transparent, centerTitle: false),
      cardTheme: CardThemeData(elevation: 0, color: Colors.white, surfaceTintColor: Colors.transparent, shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(18)))),
      inputDecorationTheme: const InputDecorationTheme(filled: true, fillColor: Colors.white, border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(14)), borderSide: BorderSide.none)),
      navigationBarTheme: const NavigationBarThemeData(height: 70, backgroundColor: Colors.white, indicatorColor: Color(0xFFDDEBFF)),
    ),
    darkTheme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF59D9FF), brightness: Brightness.dark), useMaterial3: true, cardTheme: const CardThemeData(elevation: 0), appBarTheme: const AppBarTheme(surfaceTintColor: Colors.transparent)),
    home: !state.initialized ? const Scaffold(body: Center(child: CircularProgressIndicator())) : state.onboarded ? state.callService.active ? CallScreen(state: state) : MainShell(state: state) : OnboardingScreen(state: state),
  ));
}
