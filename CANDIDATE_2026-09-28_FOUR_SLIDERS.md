# Kandidat empat slider — 28 September 2026

Basis stabil sebelumnya tetap didokumentasikan di `CHECKPOINT_2026-09-28.md`. Kandidat ini belum dinyatakan stabil sampai uji visual manual pada portal latihan selesai.

## APK yang terpasang di Vivo

| Komponen | Lokasi | SHA-256 |
| --- | --- | --- |
| E-Ujian | `outputs/four_slider_candidate_20260928_v4/E-Ujian_capture_gemini_candidate.apk` | `8AD43B2D20A5322F509F6242491483B7C8A0773BFE66B305BB9EB5462D16A4E8` |
| Settings | `outputs/four_slider_candidate_20260928_v4/E-Ujian-Settings.apk` | `4D7CBD213BA094192BC58C100EA199E4B119678D382AAA954FBD4623F65D4081` |

## Perubahan

- Posisi popup horizontal dan vertikal kini berupa slider persentase. Posisi lama dari `side` dan `bottomOffsetDp` dimigrasikan saat dibaca.
- Dots digambar sebagai kolom lurus. Slider terpisah mengatur jarak titik dalam kolom dan jarak antar kolom. Jawaban `1,5` menjadi dua kolom berisi satu dan lima titik.
- Pratinjau badge memakai ukuran konten sehingga contoh numerik `1,5` tidak lagi memenuhi lebar panel.
- Instruksi Gemini dan pemeriksaan respons membedakan radio, checkbox, dan textarea; pilihan `-` sebagai placeholder tidak dipilih. Jawaban esai diminta ringkas dan meliputi seluruh bagian pertanyaan secara urut. Respons tidak konsisten menjadi `UNCLEAR` (`?`) dan kategori kesalahannya dicatat tanpa isi soal atau jawaban.

## Verifikasi lokal

- Build modul dan Settings berhasil. Kedua APK terpasang melalui ADB pada Vivo non-root.
- `git diff --check` berhasil.
- `scripts/verify_apk.py` terhadap APK basis berhasil: satu DEX ditambah; perubahan lain adalah manifest, DEX utama, dan metadata tanda tangan.
- Hash kelas GateMethodChannelHandler, GateCheckValues, GateJniBridge, dan PairIP Application sama dengan kandidat stabil lama. Uji masuk portal siswa tetap diperlukan untuk menilai perilaku runtime.

## Uji manual yang masih diperlukan

1. Di Settings, pilih numeric dan pratinjau `1,5`. Foto panel pratinjau; badge harus selebar isi teks, bukan selebar panel.
2. Pilih dots dan pratinjau `1,5`. Foto dengan kedua jarak pada bawaan, lalu setelah mengubah masing-masing slider ke ujungnya. Kolom kiri berisi satu titik, kolom kanan lima titik.
3. Geser popup horizontal dan vertikal ke kedua ujung. Foto pratinjau pada posisi paling ekstrem; badge harus tetap seluruhnya terlihat.
4. Simpan Settings. Pada portal latihan sendiri, uji satu soal radio, satu checkbox, dan satu esai. Catat apakah radio menghasilkan satu nomor, checkbox beberapa nomor, dan hanya esai disalin ke clipboard. Gunakan dots juga pada checkbox.
5. Uji MediaProjection READY, capture satu tahap dan dua tahap, serta fallback jika service dihentikan. Periksa bahwa floating button kembali setelah setiap trigger.
6. Masuk portal siswa dan uji paste. Jika ada `?` atau `!`, ekspor diagnostic log tanpa mengirim isi soal atau API key.

Screenshot manual dari pengguna menjadi bukti visual utama. ADB dipakai untuk instalasi dan log teknis; hasil visual tidak disimpulkan dari UIAutomator dump.
