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
      fontFamily: 'Roboto',
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0EA5E9), primary: const Color(0xFF0284C7), secondary: const Color(0xFF14B8A6)),
      useMaterial3: true,
      scaffoldBackgroundColor: const Color(0xFFF8FAFC),
      appBarTheme: const AppBarTheme(backgroundColor: Color(0xFFF8FAFC), foregroundColor: Color(0xFF0F172A), surfaceTintColor: Colors.transparent, centerTitle: false, elevation: 0),
      cardTheme: const CardThemeData(elevation: 0, color: Colors.white, surfaceTintColor: Colors.transparent, shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(16)), side: BorderSide(color: Color(0xFFE2E8F0)))),
      inputDecorationTheme: const InputDecorationTheme(filled: true, fillColor: Colors.white, border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(12)), borderSide: BorderSide(color: Color(0xFFCBD5E1))), enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(12)), borderSide: BorderSide(color: Color(0xFFCBD5E1)))),
      navigationBarTheme: const NavigationBarThemeData(height: 72, backgroundColor: Colors.white, indicatorColor: Color(0xFFCCFBF1), labelTextStyle: WidgetStatePropertyAll(TextStyle(fontSize: 11, fontWeight: FontWeight.w700))),
    ),
    darkTheme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF22D3EE), brightness: Brightness.dark), useMaterial3: true, scaffoldBackgroundColor: const Color(0xFF0F172A), cardTheme: const CardThemeData(elevation: 0, color: Color(0xFF111827)), appBarTheme: const AppBarTheme(backgroundColor: Color(0xFF0F172A), surfaceTintColor: Colors.transparent), navigationBarTheme: const NavigationBarThemeData(height: 72, backgroundColor: Color(0xFF111827), indicatorColor: Color(0xFF164E63))),
    home: !state.initialized ? const LinkMeshSplashScreen() : state.onboarded ? state.callService.active ? CallScreen(state: state) : MainShell(state: state) : OnboardingScreen(state: state),
  ));
}

class LinkMeshSplashScreen extends StatelessWidget {
  const LinkMeshSplashScreen({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
    body: Container(
      decoration: const BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFFF8FAFC), Color(0xFFE2E8F0)])),
      child: SafeArea(child: Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
        ClipRRect(borderRadius: BorderRadius.circular(32), child: Image.asset('assets/images/mesh_logo.jpg', width: 160, height: 160)),
        const SizedBox(height: 22),
        const Text('LINKMESH', style: TextStyle(color: Color(0xFF0F172A), fontSize: 32, letterSpacing: 3, fontWeight: FontWeight.w900)),
        const SizedBox(height: 6),
        const Text('Stay Connected. Anywhere.', style: TextStyle(color: Color(0xFF475569), fontSize: 16, fontWeight: FontWeight.w600)),
        const SizedBox(height: 34),
        const SizedBox(width: 28, height: 28, child: CircularProgressIndicator(strokeWidth: 3, color: Color(0xFF14B8A6))),
      ]))),
    ),
  );
}
