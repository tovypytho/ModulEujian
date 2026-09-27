package id.eujian.capture.settings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class SettingsActivity extends Activity {
    private static final int PICK_CONFIG = 7;
    private Uri configUri;
    private JSONObject config;
    private int badgeOpacity = 55, badgeSize = 12, badgeBottom = 120, badgeDuration = 3500;
    private int buttonOpacity = 55, buttonSize = 52;
    private String buttonSide = "right";
    private FrameLayout preview;
    private TextView previewBadge, previewButton, fileLabel;
    private final Map<String, SeekBar> sliders = new HashMap<>();
    private int sampleAnswer = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(14), dp(16), dp(24));
        column.setBackgroundColor(0xFFF3F6F8);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
        TextView title = label("Tampilan modul E-Ujian", 22);
        column.addView(title);
        TextView help = label("Pilih config.json yang dibuat E-Ujian di Download/E-Ujian. Pratinjau ini hanya contoh tampilan; soal dan API key tidak ditampilkan.", 14);
        column.addView(help);
        fileLabel = label("Belum memilih config.json", 13);
        column.addView(fileLabel);
        Button open = action("Pilih config.json", () -> pickConfig());
        column.addView(open);
        preview = new FrameLayout(this);
        preview.setBackground(round(0xFFE3E9EC, 16));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(330));
        pp.topMargin = dp(14); pp.bottomMargin = dp(16);
        column.addView(preview, pp);
        TextView mock = label("Pratinjau layar E-Ujian\n\nTeks soal dan pilihan jawaban", 17);
        mock.setGravity(Gravity.CENTER);
        preview.addView(mock, new FrameLayout.LayoutParams(-1, -1));
        previewBadge = label("1,2", badgeSize);
        previewBadge.setTextColor(Color.WHITE);
        previewBadge.setGravity(Gravity.CENTER);
        previewBadge.setPadding(dp(7), dp(4), dp(7), dp(4));
        previewBadge.setBackground(round(0xCC182027, 8));
        preview.addView(previewBadge);
        previewButton = label("◎", 30);
        previewButton.setTextColor(Color.WHITE);
        previewButton.setGravity(Gravity.CENTER);
        previewButton.setBackground(round(0xCC263238, 28));
        preview.addView(previewButton);
        column.addView(action("Contoh jawaban: 1 → 1,2 → ✓", () -> { sampleAnswer = (sampleAnswer + 1) % 3; render(); }));
        column.addView(label("Badge jawaban", 18));
        slider(column, "Transparansi badge", 15, 100, badgeOpacity, v -> badgeOpacity = v);
        slider(column, "Ukuran teks badge (sp)", 8, 24, badgeSize, v -> badgeSize = v);
        slider(column, "Jarak badge dari bawah (dp)", 24, 240, badgeBottom, v -> badgeBottom = v);
        slider(column, "Lama tampil (ms)", 500, 10000, badgeDuration, v -> badgeDuration = v);
        column.addView(label("Tombol tangkap", 18));
        slider(column, "Transparansi tombol", 15, 100, buttonOpacity, v -> buttonOpacity = v);
        slider(column, "Ukuran tombol (dp)", 32, 88, buttonSize, v -> buttonSize = v);
        column.addView(action("Pindah tombol kiri / kanan", () -> { buttonSide = buttonSide.equals("right") ? "left" : "right"; render(); }));
        column.addView(action("Simpan ke config.json terpilih", () -> save()));
        String remembered = getPreferences(0).getString("uri", null);
        if (remembered != null) { try { load(Uri.parse(remembered)); } catch (Exception ignored) { fileLabel.setText("Pilih ulang config.json"); } }
        render();
    }

    private void pickConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_CONFIG);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_CONFIG || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            load(uri);
            getPreferences(0).edit().putString("uri", uri.toString()).apply();
        } catch (Exception ex) { toast("Gagal membuka config: " + ex.getClass().getSimpleName()); }
    }

    private void load(Uri uri) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Input null");
            byte[] bytes = new byte[4096]; int n;
            while ((n = in.read(bytes)) >= 0) { out.write(bytes, 0, n); if (out.size() > 32768) throw new Exception("Config too large"); }
        }
        JSONObject loaded = new JSONObject(out.toString("UTF-8"));
        if (!loaded.has("model") || !loaded.has("apiKeys")) throw new Exception("Bukan config modul E-Ujian");
        config = loaded; configUri = uri;
        JSONObject badge = loaded.optJSONObject("badge");
        JSONObject button = loaded.optJSONObject("button");
        if (badge != null) {
            badgeOpacity = (int) Math.round(badge.optDouble("opacity", .55) * 100);
            badgeSize = badge.optInt("textSizeSp", 12);
            badgeBottom = badge.optInt("bottomOffsetDp", 120);
            badgeDuration = badge.optInt("durationMs", 3500);
        }
        if (button != null) {
            buttonOpacity = (int) Math.round(button.optDouble("opacity", .55) * 100);
            buttonSize = button.optInt("sizeDp", 52);
            buttonSide = button.optString("side", "right");
        }
        fileLabel.setText("Config dipilih. Model: " + loaded.optString("model") + ". API key disimpan tanpa ditampilkan.");
        setSlider("Transparansi badge", badgeOpacity, 15);
        setSlider("Ukuran teks badge (sp)", badgeSize, 8);
        setSlider("Jarak badge dari bawah (dp)", badgeBottom, 24);
        setSlider("Lama tampil (ms)", badgeDuration, 500);
        setSlider("Transparansi tombol", buttonOpacity, 15);
        setSlider("Ukuran tombol (dp)", buttonSize, 32);
        render();
    }

    private void save() {
        if (configUri == null || config == null) { toast("Pilih config.json lebih dahulu"); return; }
        try {
            JSONObject badge = config.optJSONObject("badge"); if (badge == null) badge = new JSONObject();
            badge.put("opacity", badgeOpacity / 100.0).put("textSizeSp", badgeSize).put("bottomOffsetDp", badgeBottom).put("durationMs", badgeDuration);
            JSONObject button = config.optJSONObject("button"); if (button == null) button = new JSONObject();
            button.put("opacity", buttonOpacity / 100.0).put("sizeDp", buttonSize).put("side", buttonSide);
            config.put("badge", badge).put("button", button);
            byte[] bytes = config.toString(2).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = getContentResolver().openOutputStream(configUri, "rwt")) {
                if (out == null) throw new Exception("Output null"); out.write(bytes);
            }
            toast("Tersimpan. Buka ulang E-Ujian untuk menerapkan tampilan.");
        } catch (Exception ex) { toast("Gagal menyimpan: " + ex.getClass().getSimpleName()); }
    }

    private void render() {
        if (preview == null) return;
        previewBadge.setText(sampleAnswer == 0 ? "1,2" : sampleAnswer == 1 ? "1" : "✓");
        previewBadge.setTextSize(badgeSize);
        previewBadge.setAlpha(badgeOpacity / 100f);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        badgeParams.bottomMargin = dp(Math.min(240, badgeBottom) * 330 / 700);
        previewBadge.setLayoutParams(badgeParams);
        previewButton.setAlpha(buttonOpacity / 100f);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(dp(buttonSize), dp(buttonSize), (buttonSide.equals("left") ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        buttonParams.leftMargin = buttonSide.equals("left") ? dp(8) : 0;
        buttonParams.rightMargin = buttonSide.equals("right") ? dp(8) : 0;
        previewButton.setLayoutParams(buttonParams);
    }

    private interface Change { void value(int value); }
    private void slider(LinearLayout parent, String name, int min, int max, int initial, Change change) {
        TextView title = label(name + ": " + initial, 14); parent.addView(title);
        SeekBar bar = new SeekBar(this); bar.setMax(max - min); bar.setProgress(Math.max(0, Math.min(max - min, initial - min))); parent.addView(bar);
        sliders.put(name, bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int progress, boolean user) { int value = min + progress; change.value(value); title.setText(name + ": " + value); render(); }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
    }
    private void setSlider(String name, int value, int min) { SeekBar bar = sliders.get(name); if (bar != null) bar.setProgress(value - min); }
    private TextView label(String text, int size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(0xFF182027); v.setPadding(0, dp(7), 0, dp(7)); return v; }
    private Button action(String text, Runnable action) { Button b = new Button(this); b.setText(text); b.setOnClickListener(v -> action.run()); return b; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
