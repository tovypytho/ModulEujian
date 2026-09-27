package id.eujian.cbt.capture;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.SurfaceView;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A deliberately small in-process Android module. No service, overlay window or second Activity. */
public final class CaptureModule {
    private static CaptureModule instance;
    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final HandlerThread pixelThread = new HandlerThread("eujian-pixel-copy");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final TextView button;
    private final TextView status;
    private final Runnable hideStatus;
    private volatile boolean busy;
    private volatile byte[] staged;
    private volatile long stagedAt;
    private long touchDown;
    private int keyCursor;
    private volatile int badgeDurationMs = 3500;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public static void install(final Activity activity) {
        if (Build.VERSION.SDK_INT < 29) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(() -> install(activity));
            return;
        }
        if (instance != null && instance.activity == activity) return;
        if (instance != null) instance.dispose();
        try { instance = new CaptureModule(activity); }
        catch (RuntimeException ignored) { /* Never prevent the exam Activity from starting. */ }
    }

    private void dispose() {
        worker.shutdownNow();
        pixelThread.quitSafely();
    }

    private CaptureModule(Activity activity) {
        this.activity = activity;
        pixelThread.start();
        FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
        button = new TextView(activity);
        button.setText("◎");
        button.setTextSize(30);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setAlpha(0.55f);
        button.setBackground(round(0xCC263238, 28));
        button.setContentDescription("Tangkap soal; tekan lama untuk dua tahap");
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bp.rightMargin = dp(8);
        decor.addView(button, bp);
        status = new TextView(activity);
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setMaxWidth(dp(160));
        status.setPadding(dp(7), dp(4), dp(7), dp(4));
        status.setGravity(Gravity.CENTER);
        status.setAlpha(0.55f);
        status.setBackground(round(0xCC182027, 8));
        status.setVisibility(View.GONE);
        hideStatus = () -> status.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin = dp(120);
        decor.addView(status, sp);
        button.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                touchDown = android.os.SystemClock.uptimeMillis();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                long duration = android.os.SystemClock.uptimeMillis() - touchDown;
                onTrigger(duration);
                return true;
            }
            return e.getActionMasked() == MotionEvent.ACTION_MOVE || e.getActionMasked() == MotionEvent.ACTION_CANCEL;
        });
        worker.execute(() -> {
            try { ensureConfig(); Config config = readConfig(); ui.post(() -> applyAppearance(config)); }
            catch (Exception ex) { show("CFG", 7000); }
        });
    }

    private int dp(int value) { return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void onTrigger(long duration) {
        if (busy) { show("…", 2500); return; }
        final Config config;
        try { config = readConfig(); }
        catch (Exception ex) { show("CFG", 6500); return; }
        applyAppearance(config);
        boolean longPress = duration >= config.longPressMs;
        if (!longPress) staged = null;
        if (staged != null && System.currentTimeMillis() - stagedAt > 120000L) staged = null;
        final boolean stageTwo = longPress && staged != null;
        busy = true;
        button.setVisibility(View.INVISIBLE);
        status.setVisibility(View.INVISIBLE);
        FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
        // Give the window a frame to draw without either of our views.
        decor.postDelayed(() -> capture(config, longPress, stageTwo), 50);
    }

    private void capture(Config config, boolean longPress, boolean stageTwo) {
        View decor = activity.getWindow().getDecorView();
        int w = decor.getWidth();
        int h = decor.getHeight();
        if (w < 1 || h < 1) { button.setVisibility(View.VISIBLE); busy = false; show("IMG", 3000); return; }
        final SurfaceView surface = findSurface(decor);
        int sourceWidth = surface == null ? w : surface.getWidth();
        int sourceHeight = surface == null ? h : surface.getHeight();
        if (sourceWidth < 1 || sourceHeight < 1) { button.setVisibility(View.VISIBLE); busy = false; show("IMG", 3000); return; }
        final Bitmap source = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.OnPixelCopyFinishedListener listener = result -> ui.post(() -> {
                button.setVisibility(View.VISIBLE);
                if (result != PixelCopy.SUCCESS) {
                    source.recycle();
                    busy = false;
                    show("IMG", 5500);
                    return;
                }
                Bitmap composed;
                try { composed = composeSurfaceAndWebViews(decor, surface, source, w, h); }
                catch (RuntimeException ex) {
                    source.recycle();
                    busy = false;
                    show("IMG", 5500);
                    return;
                }
                source.recycle();
                if (isMostlyBlack(composed)) {
                    composed.recycle();
                    busy = false;
                    show("IMG", 5500);
                    return;
                }
                worker.execute(() -> processCapture(composed, config, longPress, stageTwo));
            });
            Handler handler = new Handler(pixelThread.getLooper());
            if (surface != null) PixelCopy.request(surface, source, listener, handler);
            else PixelCopy.request(activity.getWindow(), source, listener, handler);
        } catch (Exception ex) {
            source.recycle();
            button.setVisibility(View.VISIBLE);
            busy = false;
            show("IMG", 5500);
        }
    }

    private SurfaceView findSurface(View view) {
        if (view instanceof SurfaceView && view.getWidth() > 0 && view.getHeight() > 0) return (SurfaceView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                SurfaceView found = findSurface(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private Bitmap composeSurfaceAndWebViews(View decor, SurfaceView surface, Bitmap source, int width, int height) {
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.WHITE);
        int[] origin = new int[2];
        decor.getLocationInWindow(origin);
        if (surface != null) {
            int[] location = new int[2];
            surface.getLocationInWindow(location);
            canvas.drawBitmap(source, location[0] - origin[0], location[1] - origin[1], null);
        } else {
            canvas.drawBitmap(source, 0, 0, null);
        }
        drawWebViews(decor, canvas, origin);
        return result;
    }

    private void drawWebViews(View view, Canvas canvas, int[] origin) {
        if (view instanceof WebView && view.getVisibility() == View.VISIBLE) {
            int[] location = new int[2];
            view.getLocationInWindow(location);
            canvas.save();
            canvas.translate(location[0] - origin[0], location[1] - origin[1]);
            canvas.clipRect(0, 0, view.getWidth(), view.getHeight());
            view.draw(canvas);
            canvas.restore();
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) drawWebViews(group.getChildAt(i), canvas, origin);
        }
    }

    private boolean isMostlyBlack(Bitmap bitmap) {
        int dark = 0;
        int total = 0;
        for (int y = 0; y < bitmap.getHeight(); y += Math.max(1, bitmap.getHeight() / 20)) {
            for (int x = 0; x < bitmap.getWidth(); x += Math.max(1, bitmap.getWidth() / 20)) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.red(pixel) < 12 && Color.green(pixel) < 12 && Color.blue(pixel) < 12) dark++;
                total++;
            }
        }
        return dark > total * 95 / 100;
    }

    private void processCapture(Bitmap bitmap, Config config, boolean longPress, boolean stageTwo) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality, out)) throw new Exception("JPEG gagal");
            bitmap.recycle();
            byte[] jpeg = out.toByteArray();
            if (jpeg.length < 3000) throw new Exception("Gambar kosong");
            saveImage(jpeg, stageTwo ? "stage2" : longPress ? "stage1" : "single");
            if (longPress && !stageTwo) {
                staged = jpeg;
                stagedAt = System.currentTimeMillis();
                busy = false;
                show("1/2", 6000);
                return;
            }
            ArrayList<byte[]> images = new ArrayList<>();
            if (stageTwo) images.add(staged);
            images.add(jpeg);
            staged = null;
            show("…", 15000);
            Answer answer = askGemini(config, images);
            if (answer.kind.equals("FREE_RESPONSE")) {
                activity.runOnUiThread(() -> {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Jawaban esai", answer.text));
                });
                show("✓", 7000);
            } else if (answer.kind.equals("UNCLEAR")) {
                show("?", 6500);
            } else {
                show(answer.text, 9000);
            }
        } catch (Exception ex) {
            show(errorBadge(ex), 7000);
        } finally {
            busy = false;
        }
    }

    private void show(String message, long ms) {
        ui.post(() -> {
            status.setText(message);
            status.setVisibility(View.VISIBLE);
            ui.removeCallbacks(hideStatus);
            ui.postDelayed(hideStatus, Math.min(ms, badgeDurationMs));
        });
    }

    private void applyAppearance(Config config) {
        status.setTextSize(config.badgeTextSizeSp);
        status.setAlpha(config.badgeOpacity);
        badgeDurationMs = config.badgeDurationMs;
        FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) status.getLayoutParams();
        sp.bottomMargin = dp(config.badgeBottomOffsetDp);
        status.setLayoutParams(sp);
        button.setAlpha(config.buttonOpacity);
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) button.getLayoutParams();
        bp.width = bp.height = dp(config.buttonSizeDp);
        bp.gravity = (config.buttonSide.equals("left") ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
        bp.leftMargin = config.buttonSide.equals("left") ? dp(8) : 0;
        bp.rightMargin = config.buttonSide.equals("right") ? dp(8) : 0;
        button.setLayoutParams(bp);
    }

    private String errorBadge(Exception ex) {
        String message = safeMessage(ex);
        if (message.contains("Isi API key")) return "KEY";
        java.util.regex.Matcher http = java.util.regex.Pattern.compile("HTTP [0-9]{3}").matcher(message);
        if (http.find()) return http.group();
        if (message.contains("Semua key")) return "KEY!";
        return "!";
    }

    private static String safeMessage(Exception ex) {
        String m = ex.getMessage();
        if (m == null) return ex.getClass().getSimpleName();
        if (m.length() > 100) m = m.substring(0, 100);
        return m.replaceAll("AIza[0-9A-Za-z_-]+", "[KEY]");
    }

    private Uri ensureConfig() throws Exception {
        SharedPreferences prefs = activity.getSharedPreferences("capture_module", Context.MODE_PRIVATE);
        String old = prefs.getString("config_uri", null);
        ContentResolver resolver = activity.getContentResolver();
        if (old != null) {
            Uri uri = Uri.parse(old);
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in != null) return uri;
            } catch (Exception ignored) { /* Query for the user's original file next. */ }
        }
        Uri files = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        try (Cursor c = resolver.query(files, new String[]{MediaStore.Downloads._ID},
                MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
                new String[]{"config.json", "Download/E-Ujian/"}, null)) {
            if (c != null && c.moveToFirst()) {
                Uri uri = Uri.withAppendedPath(files, String.valueOf(c.getLong(0)));
                prefs.edit().putString("config_uri", uri.toString()).apply();
                return uri;
            }
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, "config.json");
        values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
        values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/E-Ujian/");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(files, values);
        if (uri == null) throw new Exception("Tidak dapat membuat config.json");
        try (OutputStream out = resolver.openOutputStream(uri, "w")) {
            if (out == null) throw new Exception("Tidak dapat menulis config.json");
            out.write(("{\n  \"model\": \"gemini-2.5-flash\",\n  \"jpegQuality\": 80,\n  \"longPressMs\": 650,\n"
                    + "  \"badge\": {\"opacity\": 0.55, \"textSizeSp\": 12, \"durationMs\": 3500, \"bottomOffsetDp\": 120},\n"
                    + "  \"button\": {\"opacity\": 0.55, \"sizeDp\": 52, \"side\": \"right\"},\n"
                    + "  \"apiKeys\": [\n    {\"label\": \"primary\", \"enabled\": true, \"key\": \"PASTE_KEY_HERE\"}\n  ]\n}\n")
                    .getBytes(StandardCharsets.UTF_8));
        } finally {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
        }
        prefs.edit().putString("config_uri", uri.toString()).apply();
        return uri;
    }

    private Config readConfig() throws Exception {
        Uri uri = ensureConfig();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Berkas tidak bisa dibaca");
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) >= 0) { out.write(b, 0, n); if (out.size() > 32768) throw new Exception("JSON terlalu besar"); }
        }
        JSONObject obj = new JSONObject(out.toString("UTF-8"));
        Config cfg = new Config();
        cfg.model = obj.optString("model", "gemini-2.5-flash");
        if (!cfg.model.matches("[A-Za-z0-9._-]{3,100}")) throw new Exception("Nama model tidak valid");
        cfg.jpegQuality = obj.optInt("jpegQuality", 80);
        cfg.longPressMs = obj.optInt("longPressMs", 650);
        JSONObject badge = obj.optJSONObject("badge");
        cfg.badgeOpacity = badge == null ? 0.55f : (float) badge.optDouble("opacity", 0.55);
        cfg.badgeTextSizeSp = badge == null ? 12 : badge.optInt("textSizeSp", 12);
        cfg.badgeDurationMs = badge == null ? 3500 : badge.optInt("durationMs", 3500);
        cfg.badgeBottomOffsetDp = badge == null ? 120 : badge.optInt("bottomOffsetDp", 120);
        JSONObject buttonCfg = obj.optJSONObject("button");
        cfg.buttonOpacity = buttonCfg == null ? 0.55f : (float) buttonCfg.optDouble("opacity", 0.55);
        cfg.buttonSizeDp = buttonCfg == null ? 52 : buttonCfg.optInt("sizeDp", 52);
        cfg.buttonSide = buttonCfg == null ? "right" : buttonCfg.optString("side", "right");
        if (cfg.badgeOpacity < 0.15f || cfg.badgeOpacity > 1.0f
                || cfg.badgeTextSizeSp < 8 || cfg.badgeTextSizeSp > 24
                || cfg.badgeDurationMs < 500 || cfg.badgeDurationMs > 10000
                || cfg.badgeBottomOffsetDp < 24 || cfg.badgeBottomOffsetDp > 400
                || cfg.buttonOpacity < 0.15f || cfg.buttonOpacity > 1.0f
                || cfg.buttonSizeDp < 32 || cfg.buttonSizeDp > 88
                || (!cfg.buttonSide.equals("left") && !cfg.buttonSide.equals("right")))
            throw new Exception("Pengaturan badge tidak valid");
        if (cfg.jpegQuality < 40 || cfg.jpegQuality > 95 || cfg.longPressMs < 350 || cfg.longPressMs > 3000)
            throw new Exception("Kualitas atau durasi tekan tidak valid");
        JSONArray keys = obj.optJSONArray("apiKeys");
        if (keys == null || keys.length() > 10) throw new Exception("apiKeys harus berisi maksimal 10 slot");
        for (int i = 0; i < keys.length(); i++) {
            JSONObject k = keys.getJSONObject(i);
            String secret = k.optString("key", "").trim();
            if (k.optBoolean("enabled", false) && !secret.isEmpty() && !secret.equals("PASTE_KEY_HERE"))
                cfg.keys.add(secret);
        }
        return cfg;
    }

    private void saveImage(byte[] jpeg, String suffix) throws Exception {
        ContentResolver resolver = activity.getContentResolver();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "E-Ujian_" + stamp + "_" + suffix + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/E-Ujian/");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("Album tidak tersedia");
        try (OutputStream out = resolver.openOutputStream(uri, "w")) {
            if (out == null) throw new Exception("Gagal menyimpan gambar");
            out.write(jpeg);
        } finally {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
        }
    }

    private Answer askGemini(Config cfg, ArrayList<byte[]> images) throws Exception {
        if (cfg.keys.isEmpty()) throw new Exception("Isi API key pada config.json");
        JSONObject request = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", "Read all supplied screenshots as one exam question. "
                + "If the question or options are incomplete, answer UNCLEAR; never guess. "
                + "Return only JSON: {type: MULTIPLE_CHOICE|MULTIPLE_SELECT|FREE_RESPONSE|UNCLEAR, answers: [1..5], answer: string}. "
                + "Use 1-based option indices. For an essay put the complete response in answer."));
        for (byte[] image : images) {
            JSONObject data = new JSONObject().put("mimeType", "image/jpeg")
                    .put("data", Base64.encodeToString(image, Base64.NO_WRAP));
            parts.put(new JSONObject().put("inlineData", data));
        }
        request.put("contents", new JSONArray().put(new JSONObject().put("parts", parts)));
        request.put("generationConfig", new JSONObject().put("responseMimeType", "application/json"));
        String payload = request.toString();
        if (payload.length() > 19000000) throw new Exception("Gambar terlalu besar untuk dikirim");
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + cfg.model + ":generateContent";
        int start = keyCursor++ % cfg.keys.size();
        Exception last = null;
        for (int i = 0; i < cfg.keys.size(); i++) {
            String key = cfg.keys.get((start + i) % cfg.keys.size());
            Long until = cooldowns.get(key);
            if (until != null && until > System.currentTimeMillis()) continue;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(35000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-goog-api-key", key);
                try (OutputStream out = conn.getOutputStream()) { out.write(payload.getBytes(StandardCharsets.UTF_8)); }
                int code = conn.getResponseCode();
                if (code == 400 || code == 404) throw new StopRotation("Model atau permintaan ditolak (HTTP " + code + ")");
                if (code == 429) cooldowns.put(key, System.currentTimeMillis() + 60000L);
                if (code != 200) { last = new Exception("HTTP " + code); continue; }
                ByteArrayOutputStream response = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) >= 0) { response.write(b, 0, n); if (response.size() > 1048576) throw new Exception("Respons terlalu besar"); }
                }
                JSONObject envelope = new JSONObject(response.toString("UTF-8"));
                String text = envelope.getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                return parseAnswer(new JSONObject(text));
            } catch (StopRotation ex) { throw ex; }
            catch (Exception ex) { last = ex; }
            finally { if (conn != null) conn.disconnect(); }
        }
        throw new Exception(last == null ? "Semua key sedang cooldown" : "Semua key gagal atau tidak tersedia");
    }

    private Answer parseAnswer(JSONObject obj) throws JSONException {
        String kind = obj.optString("type", "UNCLEAR");
        if (kind.equals("UNCLEAR")) return new Answer(kind, "");
        if (kind.equals("FREE_RESPONSE")) {
            String answer = obj.getString("answer").trim();
            if (answer.isEmpty() || answer.length() > 12000) throw new JSONException("Esai kosong atau terlalu panjang");
            return new Answer(kind, answer);
        }
        if (!kind.equals("MULTIPLE_CHOICE") && !kind.equals("MULTIPLE_SELECT")) throw new JSONException("Jenis jawaban tidak dikenal");
        JSONArray values = obj.getJSONArray("answers");
        if (values.length() < 1 || values.length() > 5 || (kind.equals("MULTIPLE_CHOICE") && values.length() != 1))
            throw new JSONException("Jumlah pilihan tidak valid");
        StringBuilder text = new StringBuilder();
        boolean[] seen = new boolean[6];
        for (int i = 0; i < values.length(); i++) {
            int v = values.getInt(i);
            if (v < 1 || v > 5 || seen[v]) throw new JSONException("Pilihan tidak valid");
            seen[v] = true;
            if (i > 0) text.append(',');
            text.append(v);
        }
        return new Answer(kind, text.toString());
    }

    private static final class Config {
        String model;
        int jpegQuality;
        int longPressMs;
        float badgeOpacity;
        int badgeTextSizeSp;
        int badgeDurationMs;
        int badgeBottomOffsetDp;
        float buttonOpacity;
        int buttonSizeDp;
        String buttonSide;
        final ArrayList<String> keys = new ArrayList<>();
    }
    private static final class Answer {
        final String kind, text;
        Answer(String kind, String text) { this.kind = kind; this.text = text; }
    }
    private static final class StopRotation extends Exception {
        StopRotation(String message) { super(message); }
    }
}
