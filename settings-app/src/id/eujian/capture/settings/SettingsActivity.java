package id.eujian.capture.settings;

import android.app.Activity;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

public final class SettingsActivity extends Activity {
    private static final int DARK = 0xFF1C1A20, LAVENDER = 0xFFD9BDFC, PALE = 0xFFF4EFF8, MUTED = 0xFFAAA4B1;
    private JSONObject config;
    private int selectedSlot, badgeOpacity = 55, badgeSize = 12, badgeBottom = 120, badgeDuration = 3500;
    private int buttonOpacity = 55, buttonSize = 52, sample;
    private String buttonSide = "right";
    private String buttonColor = "dark";
    private EditText modelField, keyField, labelField;
    private Switch enabledSwitch;
    private TextView slotStatus, connectionStatus, previewBadge, previewButton;
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
            badgeDuration = b.optInt("durationMs", 3500);
        }
        JSONObject btn = config.optJSONObject("button");
        if (btn != null) {
            buttonOpacity = (int)Math.round(btn.optDouble("opacity", .55) * 100);
            buttonSize = btn.optInt("sizeDp", 52); buttonSide = btn.optString("side", "right");
            buttonColor = btn.optString("color", "dark");
        }
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
        keys.addView(text("Strategi: Round Robin", 15, LAVENDER, true));
        keys.addView(text("Setiap analisis memulai dari slot berikutnya. HTTP 429 memberi cooldown 60 detik.", 13, MUTED, false)); showSlot();

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
        slider(floating, "Transparansi tombol", 15, 100, buttonOpacity, v -> buttonOpacity = v);
        slider(floating, "Ukuran tombol (dp)", 32, 88, buttonSize, v -> buttonSize = v);
        floating.addView(button("Pindah tombol kiri / kanan", false, () -> { buttonSide = buttonSide.equals("right") ? "left" : "right"; renderPreview(); }));
        floating.addView(text("Warna tombol", 14, PALE, true));
        LinearLayout colors = new LinearLayout(this); floating.addView(colors);
        String[] names = {"white", "dark", "purple", "teal", "gray"};
        for (String name : names) { Button swatch = new Button(this); swatch.setText(name); swatch.setAllCaps(false); swatch.setTextColor(name.equals("white") ? 0xFF222222 : Color.WHITE); swatch.setBackground(round(colorFor(name), 18, 0));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(44), 1); sp.setMargins(dp(2), dp(2), dp(2), dp(2)); colors.addView(swatch, sp); swatch.setOnClickListener(v -> { buttonColor = name; renderPreview(); }); }
        renderPreview();
        LinearLayout badge = card(body, "⚙  Answer Popup Appearance");
        slider(badge, "Transparansi jawaban", 15, 100, badgeOpacity, v -> badgeOpacity = v);
        slider(badge, "Ukuran teks (sp)", 8, 24, badgeSize, v -> badgeSize = v);
        slider(badge, "Posisi dari bawah (dp)", 24, 240, badgeBottom, v -> badgeBottom = v);
        slider(badge, "Durasi tampil (ms)", 500, 10000, badgeDuration, v -> badgeDuration = v);
        badge.addView(button("Reset tampilan bawaan", false, () -> {
            badgeOpacity = 55; badgeSize = 12; badgeBottom = 120; badgeDuration = 3500;
            buttonOpacity = 55; buttonSize = 52; buttonSide = "right"; buttonColor = "dark"; buildScreen();
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
    private void saveAll() {
        try {
            storeCurrentSlot();
            String model = modelField.getText().toString().trim(); if (!model.matches("[A-Za-z0-9._-]{3,100}")) { toast("Nama model tidak valid"); return; }
            config.put("model", model).put("strategy", "round_robin");
            JSONObject badge = config.optJSONObject("badge"); if (badge == null) badge = new JSONObject();
            badge.put("opacity", badgeOpacity / 100.0).put("textSizeSp", badgeSize).put("bottomOffsetDp", badgeBottom).put("durationMs", badgeDuration); config.put("badge", badge);
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
        previewBadge.setTextSize(badgeSize); previewBadge.setAlpha(badgeOpacity / 100f);
        FrameLayout.LayoutParams b = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
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
    private int colorFor(String name) {
        if ("white".equals(name)) return 0xCCF5F5F5;
        if ("purple".equals(name)) return 0xCC6C4AA6;
        if ("teal".equals(name)) return 0xCC008F87;
        if ("gray".equals(name)) return 0xCC77727D;
        return 0xCC263238;
    }
}
