# Checkpoint E-Ujian — 28 September 2026

## Pasangan APK terbaru

| Komponen | Lokasi lokal | SHA-256 |
| --- | --- | --- |
| E-Ujian dengan modul capture | `outputs/mp_candidate_20260928_fix/E-Ujian_capture_gemini_candidate.apk` | `75EC4455C2CE163A24626B89DAB3B998DA6E7D2057E9D4C2C4B8BED526C39C24` |
| E-Ujian Settings | `settings-app/build/E-Ujian-Settings.apk` | `8528199C88B7830618453B72E249DB16A036EE11AE0C66331106532404DD7B53` |

Kedua APK telah dibangun lokal dan dipasang lewat ADB pada Vivo non-root. Pengguna melaporkan pasangan ini cukup bagus dan stabil, serta belum melihat dialog **“Kebijakan Gate tidak terpenuhi”**. Ini adalah checkpoint kandidat yang berhasil menurut uji pengguna; pengujian regresi lengkap di Android 13/15 dan ketahanan service jangka panjang belum dibuktikan. APK lokal tidak disimpan di Git.

## Basis dan riwayat yang wajib dipertahankan

- Satu-satunya basis integrasi APK adalah `E-Ujian_paste_focus_no_secure_candidate.apk`, SHA-256 `F23B7C5AE44BE920FA0BB28979DC0CC4B3AF9E5D1AB0FF4467381CE7B7D74D1C`.
- Basis tersebut sudah mempertahankan perilaku portal/gate dari versi DPMODS yang sebelumnya lolos, paste esai, PairIP, callback lifecycle/fokus, package, dan pustaka native. Jangan membangun dari APK resmi atau APK mod lain saat melanjutkan checkpoint ini.
- Jalur `GateMethodChannelHandler`, `GateCheckValues`, `GateJniBridge`, PairIP, manifest terkait lisensi, dan kode native berasal dari basis. Integrasi modul tidak mengubah implementasi gate. Perubahan manifest kandidat hanya deklarasi query Settings provider dan `uses-permission` signature untuk service pasangan.
- Pemeriksaan integrasi berada di `scripts/integrate-apk.ps1` dan `scripts/verify_apk.py`. Setiap build baru harus memverifikasi hash basis, sertifikat penandatangan yang sama, diff APK, dan uji masuk portal siswa. Lolos CI source saja bukan bukti gate lolos di perangkat.
- Catatan sebelumnya tentang paste, gate, dan perbandingan APK tetap berlaku; dokumen ini menambahkan checkpoint, bukan menggantikannya.

## Implementasi saat checkpoint

- Settings meminta persetujuan MediaProjection dan menjalankan `MediaProjectionService` sebagai foreground service. E-Ujian mengambil JPEG melalui Binder/PFD dengan signature permission.
- `captureEngine=auto` mencoba MediaProjection saat service siap, lalu memakai jalur WebView lama bila tidak tersedia. `media_projection` dan `in_process` tersedia sebagai nilai konfigurasi.
- Floating button, staged dua gambar, penyimpanan album, Gemini, round robin/failover key, badge, dan clipboard tetap dikelola modul E-Ujian.
- Perbaikan terakhir mengembalikan floating button setelah capture sukses, tahap 1, maupun kegagalan; Settings menampilkan status service terbaru serta pintasan notifikasi, baterai, dan info aplikasi/autostart Vivo.
- Android dapat menghentikan sesi projection; token tidak dapat dipakai ulang setelah sesi berhenti. Pengguna perlu mengaktifkan lagi dari Settings. Status `READY` harus diperiksa sebelum mengandalkan MediaProjection.

## Source dan validasi

- Commit `e999964`: service MediaProjection dan fallback WebView.
- Commit `775c4b3`: pemulihan floating button dan pintasan pengaturan perangkat.
- Commit `fd5774d`: pembaruan status setelah aktivasi.
- Build modul, build Settings, integrasi APK, alignment, dan verifikasi tanda tangan lokal telah dijalankan. Dua package terpasang di perangkat lewat ADB.
- Pengguna mengonfirmasi stabilitas dan keberhasilan gate setelah build ini. Simpan laporan pengguna terpisah dari hasil verifikasi otomatis; jangan menganggap semua skenario staged, pemulihan service, atau Android 13 sudah lulus tanpa uji berikutnya.

## Jika pekerjaan dilanjutkan

1. Pakai pasangan APK dan source pada checkpoint ini sebagai acuan pembanding.
2. Catat hash kandidat baru dan diff terhadap basis serta checkpoint sebelum pemasangan.
3. Uji `READY`, short press, staged dua tahap, gambar album, popup/clipboard, fallback setelah service berhenti, paste esai, dan portal siswa.
4. Jika dialog gate muncul, tahan kandidat dan bandingkan dengan checkpoint ini sebelum mengubah bagian gate.
