package com.waheed.aiaagent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 10;
    private static final int REQ_SPEECH = 11;
    private static final String PREFS = "agent_settings";
    private TextView status, transcript;
    private TextToSpeech tts;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<String> conversation = new ArrayList<>();
    private AgentCore agentCore;
    private String lastCommand = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        agentCore = new AgentCore(this);
        status = findViewById(R.id.status);
        transcript = findViewById(R.id.transcript);
        Button mic = findViewById(R.id.micButton);
        Button settings = findViewById(R.id.settingsButton);
        Button info = findViewById(R.id.infoButton);

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        mic.setOnClickListener(v -> startListening());
        settings.setOnClickListener(v -> showSettings());
        info.setOnClickListener(v -> showCapabilities());
        status.setText(hasKey() ? "AI ready • Internet mode" : "Offline agent ready • Tap Speak");
        transcript.setText("Agent Core V5.0 ready.");
    }

    private boolean hasKey() {
        return !prefs.getString("api_key", "").trim().isEmpty();
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);

        EditText key = new EditText(this);
        key.setHint("API key");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(prefs.getString("api_key", ""));
        box.addView(key);

        EditText model = new EditText(this);
        model.setHint("Model");
        model.setSingleLine(true);
        model.setText(prefs.getString("model", "gpt-6-luna"));
        box.addView(model);

        new android.app.AlertDialog.Builder(this)
            .setTitle("AI Settings")
            .setMessage("V2 uses the OpenAI Responses API. For security, do not publish your key or commit it to GitHub.")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, w) -> {
                prefs.edit().putString("api_key", key.getText().toString().trim())
                    .putString("model", model.getText().toString().trim()).apply();
                status.setText(hasKey() ? "AI ready • Internet mode" : "AI key needed");
            }).show();
    }

    private void showCapabilities() {
        String message =
            "WHAT I CAN DO NOW\\n\\n" +
            "🎙 Voice\\n" +
            "• Listen to your voice commands\\n" +
            "• Speak responses aloud\\n\\n" +
            "📱 Phone & Apps\\n" +
            "• Home screen\\n" +
            "• Calculator\\n" +
            "• Clock / alarms\\n" +
            "• Gallery / Photos\\n" +
            "• Files / Downloads\\n" +
            "• Google Maps\\n" +
            "• Browser / Google search\\n" +
            "• Calendar\\n" +
            "• Email\\n" +
            "• Share menu\\n" +
            "• Phone dialer\\n" +
            "• Phone Settings\\n" +
            "• Wi-Fi / Bluetooth settings\\n" +
            "• Sound / Display / Language settings\\n\\n" +
            "💬 Communication\\n" +
            "• Open WhatsApp\\n" +
            "• Prepare a WhatsApp message for review\\n" +
            "• YouTube search\\n\\n" +
            "🤖 Agent Core\\n" +
            "• Offline command understanding\\n" +
            "• Roman Urdu / English aliases\\n" +
            "• Multiple commands in one sentence\\n" +
            "• Local command memory\\n" +
            "• Successful-command learning hints\\n" +
            "• OpenAI Responses API support\\n" +
            "• Conversation context\\n" +
            "• Internet mode\\n\\n" +
            "🔒 Safety\\n" +
            "• Uses normal Android APIs and user-triggered actions\\n" +
            "• No AccessibilityService / hidden phone control\\n\\n" +
            "More features will be added in future versions.";
        new android.app.AlertDialog.Builder(this)
            .setTitle("Waheed AI Agent • V5.0")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Waheed AI Agent");
        status.setText("Listening...");
        try { startActivityForResult(i, REQ_SPEECH); }
        catch (Exception e) { status.setText("Speech recognition unavailable"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SPEECH) return;
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) askAI(r.get(0));
            else status.setText("Ready");
        } else status.setText("Ready");
    }

    private void askAI(String heard) {
        String normalized = agentCore.normalize(heard);
        lastCommand = normalized;
        ArrayList<String> commands = agentCore.splitCommands(heard);
        if (commands.size() > 1) {
            boolean handledAny = false;
            StringBuilder results = new StringBuilder();
            for (String command : commands) {
                boolean handled = handleDeviceAction(command);
                handledAny = handledAny || handled;
                if (handled && transcript.getText() != null) {
                    if (results.length() > 0) results.append("\\n");
                    results.append(transcript.getText().toString().replace("Agent: ", ""));
                }
            }
            if (handledAny) {
                transcript.setText("You: " + heard + "\\n\\nAgent: " + results);
                return;
            }
        }
        heard = normalized;
        transcript.setText("You: " + heard);
        String learningHint = agentCore.learningHint(heard);
        if (!learningHint.isEmpty()) status.setText("Memory match found");
        if (handleDeviceAction(heard)) return;
        if (!hasKey()) {
            String memory = agentCore.memorySummary();
            String reply = "I can hear you. My offline Agent Core is active. " + memory + " Add an AI model later for natural-language reasoning.";
            status.setText("AI key needed");
            speak(reply);
            return;
        }
        status.setText("Thinking...");
        executor.execute(() -> {
            try {
                String reply = callResponsesApi(heard);
                runOnUiThread(() -> {
                    transcript.setText("You: " + heard + "\n\nAgent: " + reply);
                    status.setText("AI ready • Internet mode");
                    speak(reply);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("AI error");
                    speak("I could not reach the AI service. Please check your internet and API settings.");
                    Toast.makeText(this, "AI error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String callResponsesApi(String userText) throws Exception {
        String key = prefs.getString("api_key", "").trim();
        String model = prefs.getString("model", "gpt-6-luna").trim();
        if (model.isEmpty()) model = "gpt-6-luna";

        conversation.add(userText);
        JSONArray input = new JSONArray();
        int start = Math.max(0, conversation.size() - 12);
        for (int n = start; n < conversation.size(); n++) {
            input.put(new JSONObject().put("role", "user").put("content", conversation.get(n)));
            if (n < conversation.size() - 1) {
                input.put(new JSONObject().put("role", "assistant").put("content", "Previous assistant reply"));
            }
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("instructions", "You are Waheed AI Agent, a concise personal Android assistant. Answer naturally. Do not claim you performed a phone action unless the app actually provides that tool. When asked to perform an action that V2 cannot yet perform, clearly say it is coming in a later version.");
        body.put("input", input);

        HttpURLConnection c = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) { os.write(bytes); }

        int code = c.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
            code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(),
            StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        c.disconnect();

        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        JSONObject json = new JSONObject(sb.toString());
        String text = json.optString("output_text", "");
        if (text.isEmpty()) {
            JSONArray output = json.optJSONArray("output");
            if (output != null) {
                for (int i = 0; i < output.length(); i++) {
                    JSONObject item = output.optJSONObject(i);
                    JSONArray content = item == null ? null : item.optJSONArray("content");
                    if (content == null) continue;
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject part = content.optJSONObject(j);
                        if (part != null && "output_text".equals(part.optString("type"))) {
                            text = part.optString("text", "");
                            if (!text.isEmpty()) break;
                        }
                    }
                    if (!text.isEmpty()) break;
                }
            }
        }
        if (text.isEmpty()) text = "I received the response, but could not read its text.";
        return text;
    }

    private boolean handleDeviceAction(String raw) {
        String q = raw.trim();
        String s = q.toLowerCase(Locale.ROOT);
        try {
            if (s.equals("go back") || s.equals("back") || s.contains("go back") || s.contains("wapas jao") || s.contains("peechay jao")) {
                return localActionResult(false, "Back is not controlled automatically because this version avoids Accessibility-based phone control.");
            }
            if (s.equals("go home") || s.equals("home screen") || s.contains("go to home") || s.contains("home pe jao") || s.contains("home screen kholo")) {
                Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);
                return localActionResult(true, "Going to the home screen.");
            }
            if (s.contains("open calculator") || s.contains("calculator kholo") || s.contains("calculator open karo")) {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CALCULATOR"));
                return localActionResult(true, "Opening calculator.");
            }
            if (s.contains("open clock") || s.contains("clock kholo") || s.contains("clock open karo")) {
                startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
                return localActionResult(true, "Opening the clock alarms.");
            }
            if (s.contains("open gallery") || s.contains("gallery kholo") || s.contains("photos kholo")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("content://media/internal/images/media")));
                return localActionResult(true, "Opening photos.");
            }
            if (s.contains("open files") || s.contains("files kholo") || s.contains("file manager kholo")) {
                Intent files = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
                startActivity(files);
                return localActionResult(true, "Opening files.");
            }
            if (s.contains("open maps") || s.contains("maps kholo") || s.contains("google maps kholo")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("Google Maps"))));
                return localActionResult(true, "Opening maps.");
            }
            if (s.contains("wifi settings") || s.contains("wifi kholo") || s.contains("wifi settings kholo")) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                return localActionResult(true, "Opening Wi-Fi settings.");
            }
            if (s.contains("bluetooth settings") || s.contains("bluetooth kholo")) {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                return localActionResult(true, "Opening Bluetooth settings.");
            }
            if (s.contains("open browser") || s.contains("browser kholo") || s.contains("chrome kholo")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/")));
                return localActionResult(true, "Opening the browser.");
            }
            if (s.contains("open calendar") || s.contains("calendar kholo")) {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CALENDAR"));
                return localActionResult(true, "Opening calendar.");
            }
            if (s.contains("open downloads") || s.contains("downloads kholo")) {
                Intent downloads = new Intent(Intent.ACTION_VIEW);
                downloads.setData(Uri.parse("content://com.android.providers.downloads.documents/root"));
                if (downloads.resolveActivity(getPackageManager()) != null) {
                    startActivity(downloads);
                } else {
                    startActivity(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE));
                }
                return localActionResult(true, "Opening downloads.");
            }
            if (s.contains("date settings") || s.contains("date and time") || s.contains("date time settings")) {
                startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
                return localActionResult(true, "Opening date and time settings.");
            }
            if (s.contains("sound settings") || s.contains("sound kholo")) {
                startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
                return localActionResult(true, "Opening sound settings.");
            }
            if (s.contains("display settings") || s.contains("display kholo")) {
                startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                return localActionResult(true, "Opening display settings.");
            }
            if (s.contains("language settings") || s.contains("language kholo")) {
                startActivity(new Intent(Settings.ACTION_LOCALE_SETTINGS));
                return localActionResult(true, "Opening language settings.");
            }
            if (s.contains("open email") || s.contains("email kholo")) {
                Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
                if (email.resolveActivity(getPackageManager()) != null) startActivity(email);
                else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/")));
                return localActionResult(true, "Opening email.");
            }
            if (s.startsWith("share ")) {
                String shareText = q.replaceFirst("(?i)^share\\s*", "").trim();
                if (!shareText.isEmpty()) {
                    Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText);
                    startActivity(Intent.createChooser(share, "Share with"));
                    return localActionResult(true, "Opening the share menu.");
                }
            }
            if (s.contains("open whatsapp") || s.contains("whatsapp kholo") || s.contains("whatsapp open karo")) {
                Intent i = getPackageManager().getLaunchIntentForPackage("com.whatsapp");
                if (i == null) return localActionResult(false, "WhatsApp is not installed.");
                startActivity(i);
                return localActionResult(true, "Opening WhatsApp.");
            }
            if (s.contains("open youtube") || s.contains("youtube kholo") || s.contains("youtube open karo")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/")));
                return localActionResult(true, "Opening YouTube.");
            }
            if (s.contains("search youtube") || s.contains("youtube search")) {
                String term = q.replaceFirst("(?i).*?(search youtube|youtube search)\\s*", "").trim();
                if (term.isEmpty()) return false;
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(term))));
                return localActionResult(true, "Searching YouTube for " + term);
            }
            if (s.startsWith("search web ") || s.startsWith("google ")) {
                String term = q.replaceFirst("(?i)^(search web|google)\\s*", "").trim();
                if (term.isEmpty()) return false;
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(term))));
                return localActionResult(true, "Searching the web for " + term);
            }
            if (s.startsWith("call ") || s.startsWith("dial ")) {
                String number = q.replaceFirst("(?i)^(call|dial)\\s*", "").replaceAll("[^0-9+]", "");
                if (number.isEmpty()) return false;
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
                return localActionResult(true, "Opening the dialer for " + number);
            }
            if (s.contains("open settings") || s.contains("settings kholo") || s.contains("settings open karo")) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                return localActionResult(true, "Opening phone settings.");
            }
            if (s.contains("set alarm") || s.contains("alarm") || s.contains("alarm lagao") || s.contains("alarm laga do")) {
                Matcher m = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE).matcher(q);
                if (m.find()) {
                    int hour = Integer.parseInt(m.group(1));
                    int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
                    String ap = m.group(3);
                    if (ap != null) {
                        if (ap.equalsIgnoreCase("pm") && hour < 12) hour += 12;
                        if (ap.equalsIgnoreCase("am") && hour == 12) hour = 0;
                    }
                    if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                        Intent alarm = new Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, hour)
                            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, "Waheed AI Agent");
                        startActivity(alarm);
                        return localActionResult(true, String.format(Locale.US, "Opening alarm setup for %02d:%02d.", hour, minute));
                    }
                }
            }
            if (s.startsWith("whatsapp ") || s.startsWith("message whatsapp ")) {
                Matcher m = Pattern.compile("(?i)^(?:message\\s+)?whatsapp\\s+(\\+?\\d{8,15})\\s+(.+)$").matcher(q);
                if (m.find()) {
                    String number = m.group(1).replaceAll("[^0-9]", "");
                    String message = m.group(2).trim();
                    Intent wa = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/" + number + "?text=" + Uri.encode(message)));
                    startActivity(wa);
                    return localActionResult(true, "Opening WhatsApp with the message ready. You can review and send it.");
                }
            }
        } catch (Exception e) {
            status.setText("Action error");
            Toast.makeText(this, "Action error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private boolean localActionResult(boolean ok, String message) {
        agentCore.record(lastCommand, message, ok);
        status.setText(ok ? "Action completed" : "Action needs permission");
        transcript.setText("Agent: " + message);
        speak(message);
        return true;
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent_reply");
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
