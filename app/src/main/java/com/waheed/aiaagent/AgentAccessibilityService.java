package com.waheed.aiaagent;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class AgentAccessibilityService extends AccessibilityService {
    public static final int GLOBAL_BACK = 1;
    public static final int GLOBAL_HOME = 2;
    private static AgentAccessibilityService instance;

    @Override public void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { if (instance == this) instance = null; }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }

    public static boolean performGlobal(int action) {
        return instance != null && instance.performGlobalAction(action);
    }
}
