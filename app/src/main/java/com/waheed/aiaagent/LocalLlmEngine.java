package com.waheed.aiaagent;

public final class LocalLlmEngine {
    static {
        System.loadLibrary("waheed_native");
    }

    private LocalLlmEngine() {}

    public static native String nativeLoad(String modelPath);
    public static native String nativeGenerate(String prompt, int maxTokens);
    public static native void nativeUnload();

    public static String buildPrompt(String userText) {
        return "<|system|>\\nYou are Waheed AI Agent, a helpful personal Android assistant. "
            + "Answer clearly and concisely. Do not claim that you performed a phone action unless the app actually performed it.\\n"
            + "<|user|>\\n" + userText + "\\n<|assistant|>\\n";
    }
}
