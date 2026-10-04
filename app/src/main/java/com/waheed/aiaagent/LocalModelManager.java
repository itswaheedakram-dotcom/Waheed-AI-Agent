package com.waheed.aiaagent;

import android.app.Activity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class LocalModelManager {
    private final Activity activity;
    private static final String ASSET_MODEL = "Qwen3-0.6B-Q2_K.gguf";

    public LocalModelManager(Activity activity) {
        this.activity = activity;
    }

    public boolean isModelSelected() {
        return getModelFile().exists() && getModelFile().length() > 100000000L;
    }

    public File getModelFile() {
        return new File(new File(activity.getFilesDir(), "models"), "local-model.gguf");
    }

    public String status() {
        File f = getModelFile();
        if (!f.exists()) return "Bundled local AI model is preparing...";
        long mb = f.length() / (1024L * 1024L);
        return "Bundled Qwen3 local AI ready • " + mb + " MB";
    }

    public boolean prepareBundledModel() {
        File target = getModelFile();
        if (isModelSelected()) return true;
        File dir = target.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) return false;
        File temp = new File(dir, "local-model.gguf.tmp");
        try (InputStream in = activity.getAssets().open(ASSET_MODEL);
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.flush();
            if (!temp.renameTo(target)) {
                try (InputStream tin = new java.io.FileInputStream(temp);
                     FileOutputStream tout = new FileOutputStream(target)) {
                    while ((n = tin.read(buffer)) != -1) tout.write(buffer, 0, n);
                }
                temp.delete();
            }
            return isModelSelected();
        } catch (Exception e) {
            temp.delete();
            return false;
        }
    }
}