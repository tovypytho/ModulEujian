# ModulEujian

Modul Android kecil yang berjalan **di dalam proses dan jendela Activity E-Ujian**. Source ScreenPilot2 dipakai sebagai referensi alur; modul ini tidak memasang aplikasi, service tangkapan layar, atau overlay sistem kedua.

## Status

Source berhasil dikompilasi menjadi DEX dan diintegrasikan ke kandidat APK lokal. Verifikasi statis lulus: package/version dan sertifikat sama dengan basis; manifest serta seluruh native library identik; satu-satunya perubahan kode basis adalah panggilan `CaptureModule.install()` di `MainActivity.onCreate()`. **Smoke test pada Vivo Android 15 non-root lulus untuk startup, pembuatan JSON, capture satu dan dua tahap, serta penyimpanan gambar. Uji portal gate, paste pada soal, Gemini, dan Android 13 masih tertunda. Jangan anggap kandidat ini rilis stabil.** APK basis dan keystore tidak disimpan di repositori ini.

Basis yang dipakai: `E-Ujian_paste_focus_no_secure_candidate.apk`, SHA-256 `F23B7C5AE44BE920FA0BB28979DC0CC4B3AF9E5D1AB0FF4467381CE7B7D74D1C`.

## Perilaku

- Ketuk tombol `◎`: simpan satu tangkapan viewport E-Ujian ke `Pictures/E-Ujian/`, lalu analisis dengan Gemini.
- Tekan lama pertama: simpan tahap 1. Gulir soal. Tekan lama kedua: simpan tahap 2 lalu kirim kedua gambar dalam satu permintaan. Tahap tertunda kedaluwarsa setelah dua menit.
- Pilihan tunggal ditampilkan sebagai angka `1–5`; pilihan jamak sebagai `(1,2)`; esai otomatis disalin ke clipboard. Soal yang tidak lengkap menghasilkan `UNCLEAR`.
- Tombol dan hasil adalah View dalam jendela Activity yang sama. Saat `PixelCopy` berjalan, View modul disembunyikan sementara. Modul menyalin `FlutterSurfaceView` lalu menggambar WebView yang tertanam di atasnya; menyalin Window langsung menghasilkan gambar hitam pada Vivo V2247. Capture hanya mencakup viewport yang sedang terlihat, bukan seluruh halaman gulir.
- Seluruh tangkapan disimpan ke album, termasuk ketika analisis jaringan gagal.

## Konfigurasi non-root

Pada penggunaan pertama, aplikasi membuat `Download/E-Ujian/config.json` melalui MediaStore. Edit **berkas yang sama** dengan pengelola berkas sebelum membuka soal. Jika pengelola berkas mengganti berkas alih-alih mengedit isinya, akses URI dapat berubah; bila muncul kesalahan konfigurasi, periksa lokasi/berkasnya.

```json
{
  "model": "gemini-2.5-flash",
  "jpegQuality": 80,
  "longPressMs": 650,
  "apiKeys": [
    { "label": "primary", "enabled": true, "key": "PASTE_KEY_HERE" }
  ]
}
```

`apiKeys` menerima maksimal 10 slot. Slot aktif dipilih round robin; kegagalan yang dapat dialihkan mencoba slot berikutnya, dan HTTP 429 menunda slot tersebut selama 60 detik. HTTP 400/404 menghentikan rotasi. JSON ini berada di penyimpanan publik dan menyimpan key sebagai teks biasa; jangan taruh key nyata di Git, log, atau laporan. Modul tidak membutuhkan root.

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
