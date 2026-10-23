import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

void main() => runApp(const JarvisApp());

class JarvisApp extends StatelessWidget {
  const JarvisApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'JARVIS Home',
    debugShowCheckedModeBanner: false,
    theme: ThemeData.dark(useMaterial3: true).copyWith(
      scaffoldBackgroundColor: const Color(0xff07121d),
      colorScheme: ColorScheme.fromSeed(seedColor: Colors.cyan, brightness: Brightness.dark)),
    home: const HomePage(),
  );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final input = TextEditingController();
  final endpoint = TextEditingController();
  final devices = <String, bool>{'Bedroom Light': false, 'Living Room Light': false, 'Fan': false, 'AC': false, 'TV': false};
  int fanSpeed = 1;
  String response = 'Systems ready. Try: bedroom light on karo';
  bool busy = false;

  String offline(String text) {
    final q = text.toLowerCase().trim();
    final off = RegExp(r'\b(off|band|close|stop)\b').hasMatch(q);
    final on = RegExp(r'\b(on|chalu|chalao|start|khol)\b').hasMatch(q);
    if (q.contains('good night') || q.contains('sleep mode') || q.contains('so raha')) {
      for (final key in devices.keys) { devices[key] = false; }
      return 'Good night. All virtual devices switched off.';
    }
    String? device;
    if (q.contains('bedroom')) { device = 'Bedroom Light'; }
    else if (q.contains('living') || q.contains('drawing')) { device = 'Living Room Light'; }
    else if (q.contains('fan') || q.contains('pankha')) { device = 'Fan'; }
    else if (q.contains('ac') || q.contains('air conditioner')) { device = 'AC'; }
    else if (q.contains('tv') || q.contains('television')) { device = 'TV'; }
    else if (q.contains('light') || q.contains('batti')) { device = 'Bedroom Light'; }
    if (q.contains('status') || q.contains('haal')) {
      return devices.entries.map((e) => '${e.key}: ${e.value ? "ON" : "OFF"}').join(' | ');
    }
    if (device != null) {
      if (device == 'Fan' && q.contains('speed')) {
        final m = RegExp(r'[1-5]').firstMatch(q);
        if (m != null) {
          fanSpeed = int.parse(m.group(0)!);
          devices['Fan'] = true;
          return 'Virtual fan speed set to $fanSpeed.';
        }
      }
      if (off || on) {
        devices[device] = !off;
        return '$device turned ${off ? "OFF" : "ON"} (simulation).';
      }
      return 'Please say ON or OFF for $device.';
    }
    return '';
  }

  Future<void> send() async {
    final q = input.text.trim();
    if (q.isEmpty || busy) return;
    setState(() {
      response = offline(q);
      busy = response.isEmpty;
      input.clear();
    });
    if (!busy) return;
    final url = endpoint.text.trim();
    if (!url.startsWith('https://')) {
      setState(() { response = 'Offline command not recognized. Enter an HTTPS AI backend URL for general questions.'; busy = false; });
      return;
    }
    try {
      final r = await http.post(Uri.parse(url), headers: {'Content-Type':'application/json'}, body: jsonEncode({'message': q})).timeout(const Duration(seconds: 20));
      final body = jsonDecode(r.body);
      setState(() { response = r.statusCode == 200 ? (body['reply'] ?? 'No reply').toString() : 'AI backend error: ${r.statusCode}'; });
    } catch (_) {
      setState(() { response = 'AI server unavailable. Offline appliance commands still work.'; });
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  void dispose() { input.dispose(); endpoint.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('J.A.R.V.I.S  /  HOME'), backgroundColor: Colors.transparent),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(18), children: [
      const Icon(Icons.blur_circular, size: 94, color: Colors.cyanAccent),
      const Center(child: Text('HYBRID AGENT • SIMULATION', style: TextStyle(letterSpacing: 2, color: Colors.cyanAccent))),
      const SizedBox(height: 20),
      Card(child: Padding(padding: const EdgeInsets.all(18), child: Text(response))),
      const SizedBox(height: 12),
      TextField(controller: input, onSubmitted: (_) => send(), decoration: const InputDecoration(labelText: 'Command (English / Roman Urdu)', border: OutlineInputBorder())),
      const SizedBox(height: 10),
      FilledButton.icon(onPressed: busy ? null : send, icon: const Icon(Icons.send), label: Text(busy ? 'Thinking...' : 'Send to JARVIS')),
      const SizedBox(height: 20),
      const Text('VIRTUAL APPLIANCES', style: TextStyle(fontWeight: FontWeight.bold, letterSpacing: 1.5)),
      ...devices.keys.map((name) => Card(child: SwitchListTile(title: Text(name), subtitle: Text(devices[name]! ? 'ON' : 'OFF'), value: devices[name]!, onChanged: (v) => setState(() { devices[name] = v; response = '$name ${v ? "ON" : "OFF"} (simulation).'; })))),
      const SizedBox(height: 12),
      const Text('OPTIONAL ONLINE AI', style: TextStyle(fontWeight: FontWeight.bold)),
      const SizedBox(height: 6),
      TextField(controller: endpoint, keyboardType: TextInputType.url, decoration: const InputDecoration(labelText: 'HTTPS backend endpoint /chat', border: OutlineInputBorder())),
      const SizedBox(height: 10),
      const Text('No physical appliances are controlled. Online AI requires a separately deployed backend.', style: TextStyle(color: Colors.white60)),
    ])),
  );
}
