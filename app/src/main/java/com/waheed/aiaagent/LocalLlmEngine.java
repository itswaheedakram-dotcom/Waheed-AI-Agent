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
        return "<|im_start|>system\n"
            + "You are Waheed AI Agent, a helpful personal Android assistant. "
            + "Answer clearly and concisely. You can understand English and Roman Urdu. "
            + "Do not claim that you performed a phone action unless the app actually performed it."
            + "<|im_end|>\n"
            + "<|im_start|>user\n" + userText + "<|im_end|>\n"
            + "<|im_start|>assistant\n";
    }
}
