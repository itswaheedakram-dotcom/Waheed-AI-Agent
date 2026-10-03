package com.waheed.aiaagent;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class LocalModelManager {
    private final Activity activity;
    private static final int REQ_MODEL = 71;

    public LocalModelManager(Activity activity) {
        this.activity = activity;
    }

    public void chooseModel() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/octet-stream");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        activity.startActivityForResult(i, REQ_MODEL);
    }

    public boolean isModelSelected() {
        return getModelFile().exists();
    }

    public File getModelFile() {
        return new File(new File(activity.getFilesDir(), "models"), "local-model.gguf");
    }

    public String status() {
        File f = getModelFile();
        if (!f.exists()) return "No local GGUF model selected.";
        long mb = f.length() / (1024L * 1024L);
        return "Local GGUF model ready • " + mb + " MB";
    }

    public boolean handleResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_MODEL || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return false;
        }
        Uri uri = data.getData();
        File dir = new File(activity.getFilesDir(), "models");
        if (!dir.exists()) dir.mkdirs();
        File target = getModelFile();
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) return false;
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}