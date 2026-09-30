# Checkpoint stabil isolasi sesi — 30 September 2026

## Status dan pilihan rollback

Pengguna mengonfirmasi pasangan build numerik isolasi sesi sebagai stabil dan berhasil pada Vivo Android 15 pada 30 September 2026. Pasangan ini menjadi **checkpoint rollback utama** untuk permintaan “versi stabil terakhir” atau “checkpoint isolasi sesi”. Konfirmasi ini merupakan hasil uji pengguna; pengujian ketahanan Android 14 dan semua skenario jaringan ekstrem belum diklaim selesai.

APK dibuat pada 29 September 2026 dan dipasang ke Vivo `10DD5N05UG0002D` dengan pembaruan tanpa menghapus data. Hash APK terpasang diverifikasi cocok. Instalasi E-Ujian menghasilkan peringatan dexopt profil lama; hash memastikan APK baru tetap terpasang.

## Pasangan APK yang harus dipakai bersama

Backup utama relatif terhadap root repositori: `outputs/checkpoints/stable_session_isolation_numeric_20260930/`.

| Komponen | Nama berkas | SHA-256 |
| --- | --- | --- |
| E-Ujian | `E-Ujian_capture_gemini_candidate.apk` | `5198F68037BD0470CC9593269BC78B2EB3D5AAB19FA9A32D64CF3DAB59DBC82B` |
| Settings | `E-Ujian-Settings.apk` | `9A9E75F26B127586FD78866A74C8EA49ED9AB2CD5710D299DD6C4896744E2B07` |

Lokasi absolut backup: `C:/Users/Administrator/Documents/Codex/2026-09-26/c-users-administrator-downloads-e-ujian/work/capture_gemini_20260927/ModulEujian/outputs/checkpoints/stable_session_isolation_numeric_20260930/`.

Build asal tetap di `outputs/session_isolation_numeric_candidate_20260929/`. Nama berkas masih memuat “candidate”; identitas stabil ditentukan oleh checkpoint dan hash, bukan nama berkas. Backup numerik terdahulu tetap di `outputs/checkpoints/stable_numeric_upgrade_20260929_backup/`. Dokumentasi terdahulu tetap dipertahankan: `CHECKPOINT_2026-09-28.md` dan `CHECKPOINT_NUMERIC_2026-09-28.md`.

## Perilaku baseline

- Format jawaban numerik; klasifikasi radio, checkbox, dan textarea tetap memakai validator ketat. Placeholder opsi `-` tidak dipilih. Esai ringkas dan urut.
- Setiap trigger memperoleh ID sesi. Pembatalan Future dan koneksi diminta ketika trigger menggantikan sesi sebelumnya; popup dan clipboard memeriksa sesi aktif sebelum memperbarui hasil.
- Workflow tekan lama dua tahap tetap tersedia. Implementasi membawa stage pertama yang belum kedaluwarsa ke trigger tekan lama berikutnya; ini merupakan kelanjutan workflow staged yang disengaja. Ketukan singkat tidak membawa stage itu.
- Settings menyediakan toggle MediaProjection tanpa fallback WebView. Toggle aktif menyimpan `captureEngine=media_projection`; dimatikan menyimpan `in_process`. Konfigurasi lama `auto` masih dapat fallback sampai disimpan lewat Settings. Aktifkan toggle dan simpan untuk pemakaian wajib MediaProjection.
- Capture wajib MediaProjection gagal menampilkan `X`; pemrosesan `...`, tahap pertama `1/2`, numeric `1`/`1,3`, esai disalin `✓`, ambigu `?`, dan seluruh slot gagal `API`.
- Gate, PairIP, dan mekanisme lisensi bukan bagian perubahan checkpoint ini. Catatan portal dan baseline sebelumnya tetap menjadi rujukan regresi.

## Backup dan pemulihan

Backup memuat kedua APK, manifest hash, dokumentasi checkpoint, serta salinan source/config build repositori yang dilacak Git. Backup tidak memuat API key, konfigurasi ponsel, keystore, atau isi soal. Salinan lokal APK dan source backup tidak diunggah ke GitHub.

Untuk rollback, verifikasi hash kedua berkas terhadap tabel lalu pasang pasangan ini dengan `adb -s <serial> install --no-incremental -r -d <apk>`. Jangan uninstall/clear data jika ingin mempertahankan transparansi dan preferensi. Verifikasi kembali hash APK dari `pm path`. Izin MediaProjection perlu diaktifkan ulang di Settings bila sesi projection sudah berhenti.

## Batas catatan ini

Checkpoint membekukan hasil build yang diuji pengguna. Dokumentasi ini tidak menyatakan semua rincian rencana isolasi telah teruji formal: antrean worker, callback capture terlambat, dan shutdown sesi projection tetap perlu dibuktikan dengan log jika ada regresi. Fitur konteks materi teks masih rencana terpisah dan belum masuk APK ini.
