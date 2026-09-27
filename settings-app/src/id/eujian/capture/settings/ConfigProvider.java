package id.eujian.capture.settings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class ConfigProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://id.eujian.capture.settings.config");
    private static final String EXAM_PACKAGE = "id.eujian.cbt.lynix";

    @Override public boolean onCreate() { return true; }
    private void authorize() {
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return;
        PackageManager pm = getContext().getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null) for (String name : packages) {
            if (EXAM_PACKAGE.equals(name) && pm.checkSignatures(getContext().getPackageName(), name) == PackageManager.SIGNATURE_MATCH) return;
        }
        throw new SecurityException("Only the paired E-Ujian build can access module settings");
    }
    private File file() { return new File(getContext().getFilesDir(), "module_config.json"); }
    private String read() throws Exception {
        File f = file(); if (!f.exists()) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) >= 0) { out.write(b, 0, n); if (out.size() > 32768) throw new Exception("Config too large"); }
        }
        return out.toString("UTF-8");
    }
    private void write(String json) throws Exception {
        if (json == null || json.length() > 32768) throw new Exception("Invalid config size");
        JSONObject obj = new JSONObject(json);
        if (!obj.has("model") || !obj.has("apiKeys")) throw new Exception("Invalid config structure");
        File temp = new File(getContext().getFilesDir(), "module_config.tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) { out.write(json.getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        if (!temp.renameTo(file())) throw new Exception("Cannot commit config");
    }
    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        authorize();
        Bundle result = new Bundle();
        try {
            if ("read".equals(method)) {
                result.putString("json", read());
                if (Binder.getCallingUid() != android.os.Process.myUid())
                    getContext().getSharedPreferences("connection", 0).edit().putLong("last_exam_read", System.currentTimeMillis()).apply();
            } else if ("status".equals(method)) {
                result.putLong("lastExamRead", getContext().getSharedPreferences("connection", 0).getLong("last_exam_read", 0));
                result.putBoolean("hasConfig", read() != null);
            }
            else if ("initialize".equals(method)) {
                if (read() == null) write(extras == null ? null : extras.getString("json"));
                result.putString("json", read());
                if (Binder.getCallingUid() != android.os.Process.myUid())
                    getContext().getSharedPreferences("connection", 0).edit().putLong("last_exam_read", System.currentTimeMillis()).apply();
            } else if ("write".equals(method)) {
                if (Binder.getCallingUid() != android.os.Process.myUid()) throw new SecurityException("Write is restricted to settings app");
                write(extras == null ? null : extras.getString("json"));
                result.putBoolean("saved", true);
            } else throw new IllegalArgumentException("Unknown method");
        } catch (Exception ex) { throw new IllegalStateException("Config operation failed", ex); }
        return result;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
