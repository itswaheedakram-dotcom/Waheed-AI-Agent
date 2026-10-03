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

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        status = findViewById(R.id.status);
        transcript = findViewById(R.id.transcript);
        Button mic = findViewById(R.id.micButton);
        Button settings = findViewById(R.id.settingsButton);

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        mic.setOnClickListener(v -> startListening());
        settings.setOnClickListener(v -> showSettings());
        status.setText(hasKey() ? "AI ready • Internet mode" : "AI key needed • Tap Settings");
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
            .setNeutralButton("Accessibility", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setPositiveButton("Save", (d, w) -> {
                prefs.edit().putString("api_key", key.getText().toString().trim())
                    .putString("model", model.getText().toString().trim()).apply();
                status.setText(hasKey() ? "AI ready • Internet mode" : "AI key needed");
            }).show();
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
        transcript.setText("You: " + heard);
        if (handleDeviceAction(heard)) return;
        if (!hasKey()) {
            String reply = "I can hear you. Open Settings and add your AI API key to enable my brain.";
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
                boolean ok = AgentAccessibilityService.performGlobal(AgentAccessibilityService.GLOBAL_BACK);
                return localActionResult(ok, ok ? "Going back." : "Back action needs Accessibility access.");
            }
            if (s.equals("go home") || s.equals("home screen") || s.contains("go to home") || s.contains("home pe jao") || s.contains("home screen kholo")) {
                boolean ok = AgentAccessibilityService.performGlobal(AgentAccessibilityService.GLOBAL_HOME);
                return localActionResult(ok, ok ? "Going to the home screen." : "Home action needs Accessibility access.");
            }
            if (s.contains("open accessibility") || s.contains("accessibility settings")) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return localActionResult(true, "Opening Accessibility settings.");
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
