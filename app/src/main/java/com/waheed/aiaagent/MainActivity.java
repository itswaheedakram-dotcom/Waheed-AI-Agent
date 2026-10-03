package com.waheed.aiaagent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 10;
    private static final int REQ_SPEECH = 11;
    private TextView status, transcript;
    private TextToSpeech tts;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        transcript = findViewById(R.id.transcript);
        Button mic = findViewById(R.id.micButton);

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        mic.setOnClickListener(v -> startListening());
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
        status.setText("Ready");
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                String heard = r.get(0);
                transcript.setText("You: " + heard);
                String reply = "I heard: " + heard + ". AI connection will be added in the next build.";
                tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "agent_reply");
            }
        }
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
