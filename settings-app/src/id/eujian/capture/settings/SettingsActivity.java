package id.eujian.capture.settings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

public final class SettingsActivity extends Activity {
    private static final int DARK = 0xFF1C1A20, LAVENDER = 0xFFD9BDFC, PALE = 0xFFF4EFF8, MUTED = 0xFFAAA4B1;
    private JSONObject config;
    private int selectedSlot, badgeOpacity = 55, badgeSize = 12, badgeBottom = 120;
    private int buttonOpacity = 55, buttonSize = 52, sample;
    private String buttonSide = "right";
    private String buttonColor = "dark";
    private String badgeBackground = "dark";
    private String badgeSide = "center", answerFormat = "numeric";
    private EditText modelField, keyField, labelField;
    private Switch enabledSwitch;
    private Switch diagnosticSwitch;
    private Switch captureProbeSwitch;
    private static final int EXPORT_LOG = 17;
    private TextView slotStatus, checkResults, connectionStatus, previewBadge, previewButton;
    private int keyTimeoutSeconds = 15;
    private FrameLayout preview;
    private LinearLayout slotGrid;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFFF9F7FB);
        getWindow().setNavigationBarColor(DARK);
        try {
            Bundle loaded = getContentResolver().call(ConfigProvider.URI, "read", null, null);
            String json = loaded == null ? null : loaded.getString("json");
            config = json == null ? defaults() : new JSONObject(json);
        } catch (Exception ex) { config = defaults(); }
        readAppearance(); buildScreen();
    }
    private JSONObject defaults() {
        JSONObject obj = new JSONObject();
        try { obj.put("model", "gemini-2.5-flash").put("jpegQuality", 80).put("longPressMs", 650).put("apiKeys", new JSONArray()); }
        catch (Exception ignored) {}
        return obj;
    }
    private void readAppearance() {
        JSONObject b = config.optJSONObject("badge");
        if (b != null) {
            badgeOpacity = (int)Math.round(b.optDouble("opacity", .55) * 100);
            badgeSize = b.optInt("textSizeSp", 12); badgeBottom = b.optInt("bottomOffsetDp", 120);
            badgeBackground = b.optString("background", "dark");
            badgeSide = b.optString("side", "center");
        }
        answerFormat = config.optString("answerFormat", "numeric");
        JSONObject btn = config.optJSONObject("button");
        if (btn != null) {
            buttonOpacity = (int)Math.round(btn.optDouble("opacity", .55) * 100);
            buttonSize = btn.optInt("sizeDp", 52); buttonSide = btn.optString("side", "right");
            buttonColor = btn.optString("color", "dark");
        }
        keyTimeoutSeconds = Math.max(5, Math.min(45, config.optInt("keyTimeoutSeconds", 15)));
    }
    private void buildScreen() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(DARK);
        setContentView(page);
        TextView header = text("E-Ujian Settings", 25, 0xFF17131D, true);
        header.setPadding(dp(18), dp(25), dp(18), dp(24)); header.setBackgroundColor(0xFFF9F7FB); page.addView(header);
        ScrollView scroll = new ScrollView(this); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(14), dp(20), dp(14), dp(30)); scroll.addView(body);
        LinearLayout intro = card(body, "Modul E-Ujian");
        connectionStatus = text(connectionText(), 14, MUTED, false); intro.addView(connectionStatus);
        intro.addView(text("Buka E-Ujian sekali untuk mengimpor key lama. Setelah itu kelola semua key di sini.", 13, MUTED, false));

        LinearLayout keys = card(body, "✦  Google Gemini Key Pool");
        keys.addView(text("Hingga 10 API key aktif, dengan round robin dan failover otomatis.", 14, MUTED, false));
        keys.addView(text("Gemini Model", 14, PALE, true));
        modelField = edit(config.optString("model", "gemini-2.5-flash"), false); keys.addView(modelField);
        keys.addView(text("Pilih slot key", 14, PALE, true));
        slotGrid = new LinearLayout(this); slotGrid.setOrientation(LinearLayout.VERTICAL); keys.addView(slotGrid); refreshSlotGrid();
        slotStatus = text("", 13, LAVENDER, false); keys.addView(slotStatus);
        keys.addView(text("Label slot", 14, PALE, true)); labelField = edit("", false); keys.addView(labelField);
        keys.addView(text("API key baru (kosongkan untuk mempertahankan key lama)", 14, PALE, true));
        keyField = edit("", true); keys.addView(keyField);
        enabledSwitch = new Switch(this); enabledSwitch.setText("Slot aktif"); enabledSwitch.setTextColor(PALE); keys.addView(enabledSwitch);
        keys.addView(button("Simpan slot terpilih", true, this::saveSlot));
        keys.addView(button("Hapus key pada slot ini", false, this::clearSlot));
        keys.addView(button("Cek semua slot aktif (uji nyata)", false, this::checkAllKeys));
        checkResults = text("Hasil per slot akan tampil di sini.", 13, PALE, false); keys.addView(checkResults);
        keys.addView(text("Uji nyata mengirim satu prompt pendek tanpa gambar dan memakai sedikit quota Gemini.", 12, MUTED, false));
        keys.addView(text("Strategi: Round Robin", 15, LAVENDER, true));
        keys.addView(text("Setiap analisis memulai dari slot berikutnya. Kegagalan langsung mencoba slot lain tanpa cooldown.", 13, MUTED, false));
        slider(keys, "Batas tunggu per slot (detik)", 5, 45, keyTimeoutSeconds, v -> keyTimeoutSeconds = v); showSlot();
        diagnosticSwitch = new Switch(this); diagnosticSwitch.setText("Aktifkan diagnostic log (tanpa key/soal/jawaban)"); diagnosticSwitch.setTextColor(PALE); diagnosticSwitch.setChecked(config.optBoolean("diagnostic", false)); keys.addView(diagnosticSwitch);
        captureProbeSwitch = new Switch(this); captureProbeSwitch.setText("Capture diagnostic probe (simpan varian gambar)"); captureProbeSwitch.setTextColor(PALE); captureProbeSwitch.setChecked(config.optBoolean("captureProbe", false)); keys.addView(captureProbeSwitch);
        keys.addView(text("Probe menyimpan varian direct WebView dan surface composition ke album tanpa mengirimnya ke Gemini.", 12, MUTED, false));
        keys.addView(button("Ekspor diagnostic log", false, () -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("text/plain"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, "eujian_diagnostic.log"); startActivityForResult(intent, EXPORT_LOG);
        }));

        LinearLayout floating = card(body, "⚙  Floating Button Config");
        floating.addView(text("Pratinjau langsung tombol dan jawaban", 14, MUTED, false));
        preview = new FrameLayout(this); preview.setBackground(round(0xFFE7E1EA, 20, 0));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(300)); pp.topMargin = dp(12); pp.bottomMargin = dp(12); floating.addView(preview, pp);
        TextView mock = text("Contoh halaman soal E-Ujian\n\n1. Pilihan jawaban\n2. Pilihan jawaban", 16, 0xFF342B3A, false);
        mock.setGravity(Gravity.CENTER); preview.addView(mock, new FrameLayout.LayoutParams(-1, -1));
        previewBadge = text("1,2", badgeSize, Color.WHITE, true); previewBadge.setPadding(dp(7), dp(4), dp(7), dp(4));
        previewBadge.setBackground(round(0xCC182027, 8, 0)); preview.addView(previewBadge);
        previewButton = text("◎", 30, Color.WHITE, false); previewButton.setGravity(Gravity.CENTER);
        previewButton.setBackground(round(colorFor(buttonColor), 28, 0)); preview.addView(previewButton);
        floating.addView(button("Preview jawaban: 1 → 1,2 → ✓", false, () -> { sample = (sample + 1) % 3; renderPreview(); }));
        slider(floating, "Transparansi tombol", 0, 100, buttonOpacity, v -> buttonOpacity = v);
        slider(floating, "Ukuran tombol (dp)", 32, 88, buttonSize, v -> buttonSize = v);
        floating.addView(button("Pindah tombol kiri / kanan", false, () -> { buttonSide = buttonSide.equals("right") ? "left" : "right"; renderPreview(); }));
        floating.addView(text("Warna tombol", 14, PALE, true));
        LinearLayout colors = new LinearLayout(this); floating.addView(colors);
        String[] names = {"white", "dark", "purple", "teal", "gray"};
        for (String name : names) { Button swatch = new Button(this); swatch.setText(name); swatch.setAllCaps(false); swatch.setTextColor(name.equals("white") ? 0xFF222222 : Color.WHITE); swatch.setBackground(round(colorFor(name), 18, 0));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(44), 1); sp.setMargins(dp(2), dp(2), dp(2), dp(2)); colors.addView(swatch, sp); swatch.setOnClickListener(v -> { buttonColor = name; renderPreview(); }); }
        renderPreview();
        LinearLayout badge = card(body, "⚙  Answer Popup Appearance");
        slider(badge, "Transparansi jawaban", 0, 100, badgeOpacity, v -> badgeOpacity = v);
        slider(badge, "Ukuran teks (sp)", 8, 24, badgeSize, v -> badgeSize = v);
        slider(badge, "Posisi dari bawah (dp)", 24, 240, badgeBottom, v -> badgeBottom = v);
        badge.addView(text("Latar badge", 14, PALE, true));
        LinearLayout badgeColors = new LinearLayout(this); badge.addView(badgeColors);
        for (String name : new String[]{"none", "dark", "light"}) { Button swatch = new Button(this); swatch.setText(name); swatch.setAllCaps(false); swatch.setTextColor(name.equals("light") ? 0xFF222222 : PALE); swatch.setBackground(round(name.equals("none") ? 0x00352B40 : name.equals("light") ? 0xCCF5F5F5 : 0xCC182027, 18, 0)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1); p.setMargins(dp(2), dp(2), dp(2), dp(2)); badgeColors.addView(swatch, p); swatch.setOnClickListener(v -> { badgeBackground = name; renderPreview(); }); }
        badge.addView(text("Posisi popup jawaban", 14, PALE, true));
        LinearLayout badgeSides = new LinearLayout(this); badge.addView(badgeSides);
        for (String name : new String[]{"left", "center", "right"}) { Button swatch = new Button(this); swatch.setText(name); swatch.setAllCaps(false); swatch.setTextColor(PALE); swatch.setBackground(round(0xFF392C4B, 18, 0)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1); p.setMargins(dp(2), dp(2), dp(2), dp(2)); badgeSides.addView(swatch, p); swatch.setOnClickListener(v -> { badgeSide = name; renderPreview(); }); }
        badge.addView(text("Format jawaban pilihan", 14, PALE, true));
        LinearLayout answerFormats = new LinearLayout(this); badge.addView(answerFormats);
        for (String name : new String[]{"numeric", "dots"}) { Button swatch = new Button(this); swatch.setText(name); swatch.setAllCaps(false); swatch.setTextColor(PALE); swatch.setBackground(round(0xFF392C4B, 18, 0)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1); p.setMargins(dp(2), dp(2), dp(2), dp(2)); answerFormats.addView(swatch, p); swatch.setOnClickListener(v -> { answerFormat = name; sample = 0; renderPreview(); }); }
        badge.addView(button("Reset tampilan bawaan", false, () -> {
            badgeOpacity = 55; badgeSize = 12; badgeBottom = 120;
            buttonOpacity = 55; buttonSize = 52; buttonSide = "right"; buttonColor = "dark"; badgeBackground = "dark"; badgeSide = "center"; answerFormat = "numeric"; buildScreen();
        }));
        body.addView(button("Simpan semua pengaturan", true, this::saveAll));
    }
    private void refreshSlotGrid() {
        if (slotGrid == null) return; slotGrid.removeAllViews();
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this); slotGrid.addView(line);
            for (int col = 0; col < 5; col++) {
                int index = row * 5 + col; String key = slot(index).optString("key", "");
                boolean ready = !key.isEmpty() && !key.equals("PASTE_KEY_HERE");
                Button chip = new Button(this); chip.setAllCaps(false); chip.setText("#" + (index + 1) + (ready ? " ✓" : ""));
                chip.setTextSize(13); chip.setTextColor(index == selectedSlot ? 0xFF2D1953 : PALE);
                chip.setBackground(round(index == selectedSlot ? LAVENDER : 0xFF392C4B, 11, 0));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(45), 1); cp.setMargins(dp(2), dp(4), dp(2), dp(4)); line.addView(chip, cp);
                chip.setOnClickListener(v -> { selectedSlot = index; refreshSlotGrid(); showSlot(); });
            }
        }
    }
    private JSONObject slot(int index) {
        JSONArray array = config.optJSONArray("apiKeys"); JSONObject value = array == null ? null : array.optJSONObject(index);
        return value == null ? new JSONObject() : value;
    }
    private void showSlot() {
        if (slotStatus == null) return; JSONObject value = slot(selectedSlot); String key = value.optString("key", "");
        boolean ready = !key.isEmpty() && !key.equals("PASTE_KEY_HERE");
        slotStatus.setText("Slot #" + (selectedSlot + 1) + ": " + (ready ? "key tersimpan" : "belum ada key") + " · " + (value.optBoolean("enabled", false) ? "aktif" : "nonaktif"));
        labelField.setText(value.optString("label", "Slot " + (selectedSlot + 1))); keyField.setText("");
        enabledSwitch.setChecked(value.optBoolean("enabled", ready));
    }
    private void saveSlot() {
        try {
            storeCurrentSlot();
            saveAll();
            keyField.setText(""); refreshSlotGrid(); showSlot();
        } catch (Exception ex) { toast("Gagal menyimpan slot"); }
    }
    private void storeCurrentSlot() throws Exception {
        JSONArray array = config.optJSONArray("apiKeys"); if (array == null) array = new JSONArray();
        while (array.length() <= selectedSlot) array.put(new JSONObject());
        JSONObject value = array.getJSONObject(selectedSlot); value.put("label", labelField.getText().toString().trim());
        String nextKey = keyField.getText().toString().trim(); if (!nextKey.isEmpty()) value.put("key", nextKey);
        value.put("enabled", enabledSwitch.isChecked()); config.put("apiKeys", array);
    }
    private void clearSlot() {
        try {
            JSONArray array = config.optJSONArray("apiKeys"); if (array == null) return;
            JSONObject value = array.optJSONObject(selectedSlot); if (value == null) return;
            value.remove("key"); value.put("enabled", false); refreshSlotGrid(); showSlot(); saveAll();
        } catch (Exception ex) { toast("Gagal menghapus slot"); }
    }
    private void checkAllKeys() {
        final JSONArray snapshot = config.optJSONArray("apiKeys");
        if (snapshot == null || snapshot.length() == 0) { toast("Belum ada slot key"); return; }
        final String model = modelField.getText().toString().trim();
        if (!model.matches("[A-Za-z0-9._-]{3,100}")) { toast("Nama model tidak valid"); return; }
        checkResults.setText("Memeriksa setiap slot aktif…");
        new Thread(() -> {
            int good = 0, active = 0;
            StringBuilder report = new StringBuilder();
            for (int i = 0; i < snapshot.length(); i++) {
                JSONObject item = snapshot.optJSONObject(i); if (item == null || !item.optBoolean("enabled", false)) continue;
                String key = item.optString("key", "").trim();
                if (key.isEmpty() || key.equals("PASTE_KEY_HERE")) { report.append("Slot #").append(i + 1).append(": key kosong\n"); continue; }
                active++;
                HttpURLConnection c = null;
                long started = System.currentTimeMillis();
                try {
                    c = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent").openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(10000); c.setRequestMethod("POST"); c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("x-goog-api-key", key);
                    byte[] body = ("{\"contents\":[{\"parts\":[{\"text\":\"Reply only OK\"}]}],\"generationConfig\":{\"maxOutputTokens\":32}}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    try (java.io.OutputStream out = c.getOutputStream()) { out.write(body); }
                    int code = c.getResponseCode();
                    if (code == 200) {
                        java.io.ByteArrayOutputStream response = new java.io.ByteArrayOutputStream();
                        try (java.io.InputStream in = c.getInputStream()) {
                            byte[] buffer = new byte[4096]; int count;
                            while ((count = in.read(buffer)) >= 0) {
                                response.write(buffer, 0, count);
                                if (response.size() > 65536) throw new Exception("Respons terlalu besar");
                            }
                        }
                        JSONObject result = new JSONObject(response.toString("UTF-8"));
                        String generated = result.getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                        if (generated.trim().isEmpty()) throw new Exception("Respons kosong");
                        good++;
                    }
                    report.append("Slot #").append(i + 1).append(": HTTP ").append(code).append(code == 200 ? " · respons valid" : " · gagal");
                } catch (Exception ex) { report.append("Slot #").append(i + 1).append(": ").append(ex.getClass().getSimpleName()); }
                finally { if (c != null) c.disconnect(); }
                report.append(" (").append(System.currentTimeMillis() - started).append(" ms)\n");
            }
            final int ok = good, total = active;
            runOnUiThread(() -> { checkResults.setText("Cek selesai: " + ok + "/" + total + " slot aktif berhasil\n" + report); toast(ok == total ? "Semua key aktif" : "Lihat nomor slot yang gagal"); });
        }).start();
    }
    private void saveAll() {
        try {
            storeCurrentSlot();
            String model = modelField.getText().toString().trim(); if (!model.matches("[A-Za-z0-9._-]{3,100}")) { toast("Nama model tidak valid"); return; }
            config.put("model", model).put("strategy", "round_robin");
            config.put("keyTimeoutSeconds", keyTimeoutSeconds);
            config.put("diagnostic", diagnosticSwitch != null && diagnosticSwitch.isChecked());
            config.put("captureProbe", captureProbeSwitch != null && captureProbeSwitch.isChecked());
            JSONObject badge = config.optJSONObject("badge"); if (badge == null) badge = new JSONObject();
            badge.put("opacity", badgeOpacity / 100.0).put("textSizeSp", badgeSize).put("bottomOffsetDp", badgeBottom).put("background", badgeBackground).put("side", badgeSide); config.put("badge", badge);
            config.put("answerFormat", answerFormat);
            JSONObject button = config.optJSONObject("button"); if (button == null) button = new JSONObject();
            button.put("opacity", buttonOpacity / 100.0).put("sizeDp", buttonSize).put("side", buttonSide).put("color", buttonColor); config.put("button", button);
            Bundle extras = new Bundle(); extras.putString("json", config.toString());
            Bundle saved = getContentResolver().call(ConfigProvider.URI, "write", null, extras);
            if (saved == null || !saved.getBoolean("saved")) throw new Exception("Not saved");
            connectionStatus.setText(connectionText()); toast("Pengaturan tersimpan untuk E-Ujian");
        } catch (Exception ex) { toast("Gagal menyimpan pengaturan: " + ex.getClass().getSimpleName()); }
    }
    private String connectionText() {
        try {
            Bundle result = getContentResolver().call(ConfigProvider.URI, "status", null, null);
            long last = result == null ? 0 : result.getLong("lastExamRead", 0);
            return last == 0 ? "Config tersimpan di aplikasi ini. Belum ada pembacaan dari E-Ujian pada instalasi ini."
                    : "Terakhir dibaca E-Ujian: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(last));
        } catch (Exception ex) { return "Status koneksi E-Ujian belum tersedia."; }
    }
    private void renderPreview() {
        if (preview == null) return;
        previewBadge.setText(sample == 0 ? "1,2" : sample == 1 ? "1" : "✓");
        previewBadge.setTextSize(badgeSize); previewBadge.setAlpha(badgeOpacity / 100f); previewBadge.setBackground(round("none".equals(badgeBackground) ? 0x00352B40 : "light".equals(badgeBackground) ? 0xCCF5F5F5 : 0xCC182027, 8, 0));
        String sampleText = sample == 0 ? ("dots".equals(answerFormat) ? "•\n•\n\n•\n•" : "1,2") : sample == 1 ? ("dots".equals(answerFormat) ? "•" : "1") : "✓";
        previewBadge.setText(sampleText);
        FrameLayout.LayoutParams b = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | ("left".equals(badgeSide) ? Gravity.LEFT : "right".equals(badgeSide) ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL));
        b.bottomMargin = dp(Math.min(240, badgeBottom) * 300 / 700); previewBadge.setLayoutParams(b);
        previewButton.setAlpha(buttonOpacity / 100f); previewButton.setBackground(round(colorFor(buttonColor), 28, 0));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(buttonSize), dp(buttonSize), (buttonSide.equals("left") ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        p.leftMargin = buttonSide.equals("left") ? dp(8) : 0; p.rightMargin = buttonSide.equals("right") ? dp(8) : 0; previewButton.setLayoutParams(p);
    }
    private interface Change { void value(int v); }
    private void slider(LinearLayout parent, String name, int min, int max, int initial, Change change) {
        TextView title = text(name + ": " + initial, 14, PALE, true); parent.addView(title);
        SeekBar bar = new SeekBar(this); bar.setMax(max - min); bar.setProgress(Math.max(0, Math.min(max - min, initial - min))); parent.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int progress, boolean user) { int value = min + progress; change.value(value); title.setText(name + ": " + value); renderPreview(); }
            public void onStartTrackingTouch(SeekBar b) {} public void onStopTrackingTouch(SeekBar b) {}
        });
    }
    private LinearLayout card(LinearLayout body, String title) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(17), dp(16), dp(17));
        card.setBackground(round(DARK, 24, 0xFFBCB6C1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.bottomMargin = dp(18); body.addView(card, cp);
        card.addView(text(title, 19, PALE, true)); return card;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setPadding(0, dp(8), 0, dp(8));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    private EditText edit(String value, boolean secret) {
        EditText field = new EditText(this); field.setSingleLine(true); field.setText(value); field.setTextColor(PALE); field.setHintTextColor(MUTED);
        field.setTextSize(15); field.setPadding(dp(10), dp(8), dp(10), dp(8));
        if (secret) { field.setHint("Gemini API key"); field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); }
        field.setBackground(round(0xFF29252E, 8, 0xFF77707C)); return field;
    }
    private Button button(String title, boolean primary, Runnable action) {
        Button b = new Button(this); b.setAllCaps(false); b.setText(title); b.setTextColor(primary ? 0xFF2E1B50 : PALE); b.setTextSize(15);
        b.setBackground(round(primary ? LAVENDER : 0xFF352B40, 24, 0)); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48)); p.topMargin = dp(10); b.setLayoutParams(p); return b;
    }
    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_LOG || result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Bundle response = getContentResolver().call(ConfigProvider.URI, "exportLog", null, null);
            String log = response == null ? "" : response.getString("log", "");
            try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                if (out == null) throw new Exception("Output unavailable");
                out.write(log.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            toast("Log tersimpan di lokasi yang dipilih");
        } catch (Exception ex) { toast("Ekspor log gagal"); }
    }
    private int colorFor(String name) {
        if ("white".equals(name)) return 0xCCF5F5F5;
        if ("purple".equals(name)) return 0xCC6C4AA6;
        if ("teal".equals(name)) return 0xCC008F87;
        if ("gray".equals(name)) return 0xCC77727D;
        return 0xCC263238;
    }
}
