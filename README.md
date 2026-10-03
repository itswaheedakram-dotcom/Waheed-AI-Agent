# Waheed AI Agent — V5.0

V5.0 introduces the first real **Agent Core** layer while keeping the app policy-safe and usable without an AI API key.

## V5.0 Agent Core

- Offline command normalization for English and common Roman Urdu variations.
- Multiple user commands can be routed from one sentence using connectors such as **aur / and then / then / phir**.
- Local command memory stored on-device with SharedPreferences.
- Successful command experiences are retained as lightweight learning examples.
- Repeated successful commands can produce a memory-match hint.
- Existing user-triggered Android Intent actions remain the execution layer.
- OpenAI Responses API remains an optional online reasoning layer.
- No AccessibilityService, hidden automation, SMS/call-log permissions, package enumeration, or background phone control.

## Architecture direction

The project follows a lightweight Java adaptation of the architecture patterns found in open-source local Android agents such as Jandal AI and Prism Local: **Brain/Router → Memory → Action**. Jandal documents local memory, deterministic Android skills, tool calling and local inference; Prism Local documents on-device GGUF inference, tool dispatch and local RAG.

The next stage is to add an optional local-model adapter (GGUF/llama.cpp or LiteRT) without making the current APK dependent on a multi-gigabyte model download.

## Version

**V5.0 — Real Agent Core**

Build artifact: `Waheed-AI-Agent-v5.0-safe-debug.apk`
