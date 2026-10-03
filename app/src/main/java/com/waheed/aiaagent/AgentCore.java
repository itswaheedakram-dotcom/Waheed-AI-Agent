package com.waheed.aiaagent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public final class AgentCore {
    private static final String PREFS = "agent_core";
    private static final String HISTORY = "history";
    private static final int MAX_HISTORY = 40;

    private final SharedPreferences prefs;

    public AgentCore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replace("’", "'").replace("‘", "'");
        s = s.replaceAll("[!?.,]+", " ");
        s = s.replaceAll("\s+", " ").trim();

        // Common Roman Urdu command aliases.
        s = s.replace("kholo na", "kholo");
        s = s.replace("open krdo", "open karo");
        s = s.replace("open kardo", "open karo");
        s = s.replace("khol do", "kholo");
        s = s.replace("kholna", "kholo");
        s = s.replace("krna", "karna");
        s = s.replace("krdo", "kardo");
        s = s.replace("kr do", "kardo");
        s = s.replace("lagao", "laga do");
        return s;
    }

    public ArrayList<String> splitCommands(String raw) {
        ArrayList<String> parts = new ArrayList<>();
        String normalized = normalize(raw);
        if (normalized.isEmpty()) return parts;

        // Keep natural-language search phrases intact.
        String[] chunks = normalized.split("\s+(?:aur|and then|then|phir)\s+");
        for (String chunk : chunks) {
            String c = chunk.trim();
            if (!c.isEmpty()) parts.add(c);
        }
        return parts;
    }

    public void record(String command, String result, boolean success) {
        try {
            JSONArray history = readHistory();
            JSONObject item = new JSONObject();
            item.put("command", normalize(command));
            item.put("result", result == null ? "" : result);
            item.put("success", success);
            item.put("time", System.currentTimeMillis());
            history.put(item);

            int first = Math.max(0, history.length() - MAX_HISTORY);
            JSONArray trimmed = new JSONArray();
            for (int i = first; i < history.length(); i++) {
                trimmed.put(history.getJSONObject(i));
            }
            prefs.edit().putString(HISTORY, trimmed.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public ArrayList<String> recentSuccessfulCommands(int limit) {
        ArrayList<String> result = new ArrayList<>();
        try {
            JSONArray history = readHistory();
            for (int i = history.length() - 1; i >= 0 && result.size() < limit; i--) {
                JSONObject item = history.getJSONObject(i);
                if (item.optBoolean("success", false)) {
                    String command = item.optString("command", "");
                    if (!command.isEmpty() && !result.contains(command)) result.add(command);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public String memorySummary() {
        try {
            JSONArray history = readHistory();
            int successful = 0;
            for (int i = 0; i < history.length(); i++) {
                if (history.getJSONObject(i).optBoolean("success", false)) successful++;
            }
            return history.length() + " command experiences stored locally; " +
                    successful + " completed successfully.";
        } catch (Exception e) {
            return "Local memory is ready.";
        }
    }

    public String learningHint(String command) {
        String normalized = normalize(command);
        for (String previous : recentSuccessfulCommands(12)) {
            if (previous.equals(normalized)) {
                return "I remember a successful way to handle this command.";
            }
        }
        return "";
    }

    private JSONArray readHistory() {
        try {
            return new JSONArray(prefs.getString(HISTORY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
