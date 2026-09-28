# Kandidat diagnosis badge dan sentuhan — 28 September 2026

APK E-Ujian: `outputs/badge_touch_diagnostic_20260928_v2/E-Ujian_capture_gemini_candidate.apk`, SHA-256 `3DFB092BFB52E35EB370D91530A2035712222F3C37275A27F2A86C4F6D4B0232`.

Settings tetap pasangan dari `outputs/four_slider_candidate_20260928_v4/E-Ujian-Settings.apk`.

APK dipasang lewat ADB pada Vivo. Hash berkas APK yang terpasang sama dengan hash lokal. Integrasi lulus verifikasi tanda tangan dan diff APK: satu DEX tambahan; manifest, DEX utama, dan metadata tanda tangan berubah seperti integrasi sebelumnya.

Perubahan:

- Target sentuh tombol minimal 48 dp meskipun ukuran visual dipilih 32 dp. Gambar tombol diberi inset sehingga ukuran visual tetap mengikuti Settings.
- Badge dinaikkan ke lapisan depan dan margin lama dibersihkan saat memakai posisi slider.
- Logcat tag `EujianCapture` mencatat kategori badge serta posisi, ukuran, dan visibilitas View tanpa isi soal, jawaban, atau API key.

Log ekspor sebelum perbaikan memperlihatkan trigger tahap 1 dan 2, permintaan Gemini, serta jawaban esai yang benar-benar tersalin. Tidak ditemukan crash aplikasi pada logcat saat diagnosis. Beberapa permintaan terlambat karena banyak slot API mengembalikan kegagalan HTTP sebelum slot lain berhasil. Pada uji setelah pemasangan, logcat mencatat badge `stage1`, `processing`, dan `numeric` berstatus visible pada x sekitar 372–384 dan y=2095 dari decor 1080×2388. Pengguna belum mengonfirmasi secara visual apakah badge terlihat pada layar; kandidat belum stabil.

Uji berikutnya: ubah posisi vertikal popup ke 50% dan ambil screenshot manual layar penuh saat `1/2` dan setelah jawaban. Jika masih tidak terlihat, bandingkan koordinat log dengan tampilan; periksa SurfaceView/lapisan portal dan kemungkinan badge ditutup oleh View lain. Jangan menyimpulkan masalah API key dari badge tidak terlihat.

## Pembaruan diagnostik HTTP

Kandidat terbaru: `outputs/badge_touch_http_diagnostic_20260928/E-Ujian_capture_gemini_candidate.apk`, SHA-256 `E0A8658B23872AE8015CA3611C55EA40EB987F9E4091F995277423F712210BD5`. Sudah terpasang dan hash APK di perangkat cocok.

Pada percobaan 23:42:34–23:43:07, kedua gambar staged berhasil dibuat, lalu sepuluh slot berturut-turut gagal dengan respons HTTP. Karena log lama hanya menulis kategori `http`, penyebab tiap slot belum dapat dibedakan. Kandidat terbaru menambahkan `httpStatus` berupa angka per slot (0 jika tidak ada kode HTTP) dan badge `API` ketika seluruh slot gagal. Badge `!` umum sebelumnya berasal dari pemetaan pesan "Semua ... slot gagal" yang tidak tepat. Kode status, bukan isi respons, dicatat; API key dan konten soal tidak ditulis.
