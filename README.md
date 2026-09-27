# ModulEujian

Modul Android kecil yang berjalan **di dalam proses dan jendela Activity E-Ujian**. Source ScreenPilot2 dipakai sebagai referensi alur; modul ini tidak memasang aplikasi, service tangkapan layar, atau overlay sistem kedua.

## Status

Source berhasil dikompilasi menjadi DEX dan diintegrasikan ke kandidat APK lokal. Verifikasi statis lulus: package/version dan sertifikat sama dengan basis; manifest serta seluruh native library identik; satu-satunya perubahan kode basis adalah panggilan `CaptureModule.install()` di `MainActivity.onCreate()`. **Kandidat sebelumnya telah diuji pada Vivo Android 15 non-root: startup, portal siswa, paste esai, capture satu dan dua tahap, penyimpanan gambar, serta Gemini dan clipboard esai berhasil. Kandidat dengan badge bawah dan aplikasi pengaturan sudah terpasang tanpa crash. Pemilih berkas membuka config aktif, penyimpanan mempertahankan model dan satu key aktif, dan E-Ujian dapat dibuka ulang. Portal siswa serta jawaban Gemini pada build terbaru dan Android 13 belum diuji.** APK basis dan keystore tidak disimpan di repositori ini.

Basis yang dipakai: `E-Ujian_paste_focus_no_secure_candidate.apk`, SHA-256 `F23B7C5AE44BE920FA0BB28979DC0CC4B3AF9E5D1AB0FF4467381CE7B7D74D1C`.

## Perilaku

- Ketuk tombol `◎`: simpan satu tangkapan viewport E-Ujian ke `Pictures/E-Ujian/`, lalu analisis dengan Gemini.
- Tekan lama pertama: simpan tahap 1. Gulir soal. Tekan lama kedua: simpan tahap 2 lalu kirim kedua gambar dalam satu permintaan. Tahap tertunda kedaluwarsa setelah dua menit.
- Badge kecil di bawah menampilkan angka `1–5` untuk pilihan tunggal, `1,2` untuk pilihan jamak, `✓` saat esai sudah disalin, dan `?` bila soal belum jelas. Status singkat: `…` sedang memproses, `1/2` tahap pertama tersimpan, `KEY` API key belum diisi, `CFG` konfigurasi bermasalah, `IMG` tangkapan gagal, `HTTP NNN` kegagalan jaringan, `!` kesalahan lain.
- Tombol dan hasil adalah View dalam jendela Activity yang sama. Saat `PixelCopy` berjalan, View modul disembunyikan sementara. Modul menyalin `FlutterSurfaceView` lalu menggambar WebView yang tertanam di atasnya; menyalin Window langsung menghasilkan gambar hitam pada Vivo V2247. Capture hanya mencakup viewport yang sedang terlihat, bukan seluruh halaman gulir.
- Seluruh tangkapan disimpan ke album, termasuk ketika analisis jaringan gagal.

## Konfigurasi non-root

Pada penggunaan pertama, aplikasi membuat `Download/E-Ujian/config.json` melalui MediaStore. Edit **berkas yang sama** dengan pengelola berkas sebelum membuka soal. Jika pengelola berkas mengganti berkas alih-alih mengedit isinya, akses URI dapat berubah; bila muncul kesalahan konfigurasi, periksa lokasi/berkasnya.

Pada Vivo Android 15, `adb push` langsung ke path Downloads membuat berkas baru yang tidak terkait dengan URI MediaStore milik aplikasi. Akibatnya aplikasi masih membaca template tanpa key, bahkan ketika file pada path tersebut tampak sudah berubah. Untuk mengirim JSON dari laptop, gunakan entri MediaStore milik aplikasi:

```powershell
python ./scripts/push_config.py '<config-lokal>.json' --dry-run
python ./scripts/push_config.py '<config-lokal>.json'
```

Skrip memvalidasi format, menulis lewat `content://media/.../downloads/<id>`, lalu membandingkan hasil baca balik tanpa menampilkan key. Bila ada lebih dari satu entri `config*.json`, tentukan `--media-id` dari hasil query metadata MediaStore. Jangan gunakan `adb push` ke path Downloads untuk pembaruan selanjutnya.

```json
{
  "model": "gemini-2.5-flash",
  "jpegQuality": 80,
  "longPressMs": 650,
  "badge": {"opacity": 0.55, "textSizeSp": 12, "durationMs": 3500, "bottomOffsetDp": 120},
  "button": {"opacity": 0.55, "sizeDp": 52, "side": "right"},
  "apiKeys": [
    { "label": "primary", "enabled": true, "key": "PASTE_KEY_HERE" }
  ]
}
```

`apiKeys` menerima maksimal 10 slot. Slot aktif dipilih round robin; kegagalan yang dapat dialihkan mencoba slot berikutnya, dan HTTP 429 menunda slot tersebut selama 60 detik. HTTP 400/404 menghentikan rotasi. JSON ini berada di penyimpanan publik dan menyimpan key sebagai teks biasa; jangan taruh key nyata di Git, log, atau laporan. Modul tidak membutuhkan root.

`badge` opsional. Nilai bawaan menempatkan badge di tengah bawah, sekitar 120 dp dari tepi bawah seperti pola popup ScreenPilot. `opacity` menerima 0.15–1.0, `textSizeSp` 8–24, `durationMs` 500–10000, dan `bottomOffsetDp` 24–400. `button` opsional: `opacity` 0.15–1.0, `sizeDp` 32–88, dan `side` `left` atau `right`. Konfigurasi lama tanpa kedua objek tetap memakai nilai bawaan.

## Aplikasi pengaturan terpisah

`settings-app/` adalah aplikasi Android terpisah dengan kartu gelap bergaya ScreenPilot. Ia menyediakan pratinjau layar, contoh badge `1`/`1,2`/`✓`, slider transparansi dan ukuran, posisi badge dari bawah, durasi, sisi tombol, dan pilihan warna tombol `white`, `dark`, `purple`, `teal`, atau `gray`. Ia juga menyediakan 10 slot Gemini, label, toggle aktif, penggantian/penghapusan key, dan strategi round robin. Config disimpan di penyimpanan internal Settings melalui ContentProvider bertanda tangan; E-Ujian pasangan membaca config itu tanpa pemilih file. Pada pemakaian pertama, E-Ujian menyalin config lama beserta key aktif ke store Settings. API key tidak ditampilkan kembali dan provider menolak aplikasi lain. Buka ulang E-Ujian untuk menerapkan perubahan tampilan; pemicu berikutnya juga membaca config terbaru.

Bangun dan sign aplikasi pengaturan secara lokal dengan `./settings-app/build.ps1`. GitHub Actions membangun APK pengaturan **unsigned** agar keystore lokal tidak diunggah. APK pengaturan menggunakan package `id.eujian.capture.settings` dan tidak memerlukan root atau izin overlay sistem.

## Build modul

Prasyarat: JDK 21, Android SDK platform 35 dan build-tools 35.0.1. Di Windows:

```powershell
./scripts/build-module.ps1 -AndroidSdk C:\Android
```

Hasil: `build/module/classes.dex`. Workflow GitHub Actions hanya membangun dan mengunggah DEX ini; ia **tidak** menandatangani atau merilis APK E-Ujian. Integrasi APK dilakukan pada salinan basis lokal:

```powershell
$env:EUJIAN_KS_PASS = '<password-keystore-lokal>'
./scripts/integrate-apk.ps1 -BaseApk '<apk-basis>' -Keystore '<keystore-yang-sama>' -OutputDirectory '<workbench-baru>'
Remove-Item Env:EUJIAN_KS_PASS
```

Skrip memeriksa hash basis, membuat backup, lalu menjalankan langkah berikut:

1. Verifikasi SHA-256 basis dan buat backup.
2. Dekode salinan dengan apktool. Pada `id/exambro/cbt/MainActivity.smali`, tambahkan `invoke-static {p0}, Lid/eujian/cbt/capture/CaptureModule;->install(Landroid/app/Activity;)V` sesudah `GateJniBridge.attach()` pada `onCreate()`.
3. Build APK unsigned. Tambahkan DEX modul sebagai `classes3.dex` tanpa mengubah DEX lain, lalu `zipalign -P 16` dan sign dengan keystore **yang sertifikatnya sama** dengan basis.
4. Jalankan `scripts/verify_apk.py` dan bandingkan smali kelas gate, PairIP, FlutterActivity, dan paste dengan basis. Tolak kandidat jika ada perubahan selain satu panggilan Activity dan DEX baru.

Jangan mengunggah APK basis, APK kandidat, keystore, kredensial portal, atau `config.json` berisi key ke repositori ini.

## Uji penerimaan pada perangkat

Uji di Vivo Android 13 dan 15: login portal siswa, paste esai, satu/dua tahap tangkapan, isi gambar di album, pilihan tunggal/jamak, clipboard esai, rotasi key, jaringan gagal, serta peringatan fokus saat tombol modul dan saat Back/Home/panel atas. Bila gate, paste, atau fokus berubah, tahan kandidat dan gunakan APK basis yang dicadangkan.
