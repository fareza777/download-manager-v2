# F7 Download Manager — Full Audit

Tanggal: 11 November 2026
Cakupan: seluruh `app/src/main/java` (5.731 baris), manifest, resource XML, build config, dan satu
screenshot perangkat nyata (Redmi 2201117TY, Android 13, 1080×2400). Belum termasuk profiling
`gfxinfo`/Macrobenchmark, uji jaringan lambat, atau uji baterai — itu ada di rencana pengukuran.

Kesimpulan singkat: fondasinya **di atas rata-rata** untuk aplikasi seukuran ini. Engine benar-benar
multi-koneksi dengan work stealing, validasi ETag/If-Range, resume per-segmen, HLS AES-128, SAF +
MediaStore tanpa izin storage lebar, dan APK release hanya **1,51 MB**. Masalahnya bukan fondasi,
melainkan **konsistensi status, akurasi progres HLS, ketahanan proses panjang, dan kedalaman fitur
queue**. Visualnya sudah punya arah, tapi masih ada elemen dekoratif dan label yang redundan.

---

## 0. Status implementasi (11 November 2026)

Sudah dikerjakan dan diverifikasi di perangkat (Redmi 2201117TY, Android 13):

- **Integritas data** — resume HLS tidak lagi bisa memotong awal file (B1), item CANCELED tidak
  muncul kembali setelah restart (B2), kecepatan HLS nyata (B4), progres HLS ditampilkan sebagai
  segmen (B5), wakelock diperbarui otomatis (B6), fase menyimpan ke storage punya status dan
  progres sendiri (B7), cek ruang kosong sebelum mulai (B8), key HLS divalidasi (B10).
- **Constraint & ketahanan** — Wi-Fi only, charging only, jeda otomatis saat jaringan hilang dan
  lanjut saat kembali, auto-retry dengan exponential backoff, sanitasi header dari browser.
- **Fitur queue** — pencarian, sort (terbaru/terlama/nama/ukuran), filter Paused terpisah, mode
  pilih dengan aksi batch (pause/resume/hapus), baris dengan host + ukuran + ETA + retry,
  kartu idle menampilkan ruang kosong dan jumlah selesai hari ini.
- **Settings** — tujuh opsi baru yang benar-benar berfungsi (Wi-Fi only, charging only, auto retry,
  jumlah retry, live progress notification, ambient glow, konfirmasi hapus) plus partial files dan
  ruang kosong di tab Storage.
- **Privasi** — database unduhan (berisi header/Cookie) dikeluarkan dari cloud backup dan transfer.

Bukti: unduhan nyata 1 MB selesai utuh (`1Mb.dat`, 1.048.576 byte terdaftar di MediaStore), tanpa
crash. Belum dikerjakan: HLS audio rendition/paralel/remux, DASH, torrent, OkHttp/WorkManager,
preview media, widget, dan layout tablet.

## 1. Skor per area

| Area | Nilai | Catatan |
|---|---|---|
| Engine HTTP segmented | 8/10 | Range asli, work stealing, validator file berubah |
| HLS | 5/10 | AES-128 & fMP4 jalan, tapi audio terpisah, resume, dan progres bermasalah |
| Ketahanan (service/wakelock) | 6/10 | FGS benar, wakelock 30 menit tanpa perpanjangan |
| Penyimpanan | 8/10 | SAF → MediaStore → app dir, nama unik, tanpa izin lebar |
| UI/visual | 7,5/10 | Arah jelas, motion halus, beberapa elemen redundan |
| Aksesibilitas | 6/10 | Sebagian besar ikon punya deskripsi, status masih berbasis warna + teks |
| Fitur queue | 5/10 | Tanpa Wi-Fi only, auto-retry, search/sort, batch |
| Keamanan/privasi | 5/10 | Header (Cookie) tersimpan plaintext dan ikut cloud backup |
| Build/size | 9/10 | R8 aktif, 1,51 MB, signing terverifikasi |

---

## 2. Bug dan risiko (prioritas berdasarkan dampak)

### P0 — korupsi data / kehilangan hasil

**B1. Resume HLS bisa menghasilkan file rusak.**
`HlsDownloader.run()` menyimpan `resumeAt` dari pseudo-segmen, lalu:
`if (resumeAt == 0) out.delete() else if (!out.exists()) out.delete()`.
Kalau file output hilang (dibersihkan sistem, staging dihapus, proses mati sebelum flush), file
kosong tetap ditulis mulai segmen `resumeAt` → bagian awal hilang tanpa error.
**Perbaikan:** jika `!out.exists() || out.length() == 0` paksa `resumeAt = 0`; simpan juga jumlah
segmen yang selesai di sisi file (mis. `.hlsmeta` dengan index + hash playlist).

**B2. `clearFinished()` tidak menghapus status CANCELED dari database.**
Engine menghapus CANCELED dari memori, `DownloadStore.clearFinished()` hanya
`status IN ('COMPLETED','FAILED')`. Setelah proses restart, item canceled muncul lagi.
**Perbaikan:** satu definisi "finished" yang dipakai engine dan store, atau
`status NOT IN ('QUEUED','CONNECTING','DOWNLOADING','PAUSED')`.

**B3. Cloud backup memuat `f7.db` yang berisi header Cookie/Authorization.**
`backup_rules.xml` dan `data_extraction_rules.xml` meng-include seluruh database, sementara
`DownloadStore` menyimpan `headers` sebagai teks biasa. Cookie sesi situs ikut tercadangkan ke
Google Drive dan ikut device transfer.
**Perbaikan:** enkripsi nilai header (EncryptedSharedPreferences/Keystore) atau keluarkan kolom
`headers` sebelum backup; minimal exclude DB dari cloud backup, sertakan hanya preferensi.

### P1 — salah tampil / gagal senyap

**B4. Kecepatan HLS selalu 0 B/s.**
Ticker melewati item HLS: `if (item.isHls) return@forEach`. Akibatnya notifikasi dan kartu speed
tidak pernah menunjukkan throughput HLS, dan ETA memang `null` untuk HLS.

**B5. Progres HLS ditampilkan sebagai byte.**
`onSegmentProgress` menulis `downloadedBytes = jumlah segmen`, `totalBytes = total segmen`.
UI memformat dengan `Fmt.bytes` → muncul "5 B / 300 B" untuk video 300 segmen. Butuh field
terpisah (`segmentsDone`/`segmentsTotal`) atau format khusus HLS ("5 / 300 segmen").

**B6. Wakelock tidak diperpanjang.**
`DownloadService.setWake(true)` memanggil `acquire(30 * 60 * 1000L)` sekali. Unduhan > 30 menit
kehilangan jaminan CPU (transfer bisa melambat/berhenti saat layar mati).
**Perbaikan:** timeout lebih panjang + reacquire saat `isHeld` hampir habis, atau `acquire()` tanpa
timeout dengan pelepasan eksplisit di `onDestroy`.

**B7. Fase menyalin ke penyimpanan tidak terlihat.**
`finishAndSave` memanggil `storage.save(...) { _, _ -> }` — progres copy tidak pernah dilaporkan.
File 2 GB bisa tampak "100% / selesai" selama puluhan detik tanpa indikator.
**Perbaikan:** status `SAVING`, progres copy ke notifikasi + baris, dan tombol batal yang aman.

**B8. Tidak ada cek ruang kosong sebelum mulai.**
`Storage.freeBytes()` tidak dipanggil di jalur `add()`/`execute()`. Disk penuh baru ketahuan saat
tulis gagal, dengan pesan generik.

**B9. Resume server tanpa Range selalu mengulang dari nol, tanpa penjelasan.**
`singleStream()` punya `var written = ...; if (known) written = 0L` (baris pertama jadi dead code).
Perilakunya disengaja, tetapi UI tidak memberi tahu bahwa resume akan mengunduh ulang.

**B10. Key HLS diambil tanpa validasi status/redirect.**
`fetch()` untuk `#EXT-X-KEY` membaca `inputStream` mentah; respons 302/403 bisa masuk sebagai
"key" dan dekripsi gagal dengan pesan membingungkan.

**B11. `#EXT-X-MEDIA` (audio rendition terpisah) diabaikan.**
Banyak stream HLS memisahkan audio. Hasil gabungan hanya video tanpa suara, dan aplikasi
melaporkan "completed". Ini gap paling terasa untuk pengguna.

**B12. `#EXT-X-BYTERANGE` tidak didukung.**
Playlist yang memakai byte range akan salah dipetakan (segmen dianggap file utuh).

### P2 — kualitas kode / perawatan

- **B13.** `DownloadStore.onUpgrade` kosong; `DB_VERSION` 1. Perubahan skema berikutnya akan
  menimbulkan crash tanpa jalur migrasi.
- **B14.** `Settings.language` + `setLanguage()` tidak pernah diterapkan ke locale runtime —
  konfigurasi mati (aplikasi memang English-only).
- **B15.** Deteksi duplikat hanya berdasarkan URL asli, bukan `finalUrl` setelah redirect.
- **B16.** `Http.getText()` membaca seluruh playlist tanpa batas ukuran (playlist besar/live bisa
  memakan memori).
- **B17.** `usesCleartextTraffic="true"` global, termasuk engine; redirect https→http diterima.
- **B18.** `tools:targetApi="34"` di manifest sudah tidak sesuai `targetSdk 36`.
- **B19.** `maybeChecksum()` langsung meng-hash seluruh file setelah selesai, bersaing I/O dengan
  unduhan berikutnya. Sebaiknya ditunda atau dijalankan saat idle/charging.
- **B20.** Pesan error HLS/HTTP dipetakan ke beberapa string generik; kode HTTP 429/416 dan
  `Retry-After` tidak ditangani khusus.

---

## 3. Audit visual

### Yang sudah bagus
- Arah "precision editorial" konsisten: paper, ink, satu aksen jade, angka tabular.
- Motion kecil dan fungsional (press scale, progress, tick segmen, indikator nav) — tidak berlebihan.
- Speed console adalah pembeda yang jelas; angka besar + tick segmen terasa seperti alat ukur.
- Empty state sekarang bersih setelah hero F7 dihapus.
- Dark mode punya penyesuaian aksen (lift 32%), bukan sekadar membalik warna.

### Yang perlu diperbaiki
1. **Nuansa mint pada latar.** Shader ambient memberi cast hijau di atas/bawah, sementara brand
   ingin "kertas hangat". Pilih salah satu: matikan shader di layar idle, atau ganti warna glow ke
   warm neutral dengan alpha ≤ 0,05.
2. **Label "Idle" dobel** — pill di header dan teks di kartu speed. Cukup satu.
3. **Dua CTA untuk aksi yang sama** — "Add a download" di empty state dan FAB "New download".
   Empty state sebaiknya satu aksi primer + satu aksi sekunder ("Paste link").
4. **Filter "Active" mencampur PAUSED** (`isActive || QUEUED || PAUSED`). Pengguna membuka
   "Active" lalu melihat item yang justru berhenti. Pisahkan `Paused` atau ganti label.
5. **Tidak ada search, sort, atau multi-select.** Untuk 50+ item, daftar jadi tidak terbaca.
6. **Swipe tidak bisa ditemukan.** Tidak ada affordance/hint, tidak ada haptic saat aksi terpicu,
   dan tidak ada undo (hanya dialog hapus).
7. **Baris kurang informasi:** ETA, jumlah retry, pesan error lengkap, checksum, host sumber,
   ukuran akhir, dan aksi "buka folder" tidak tersedia di baris.
8. **Tidak ada halaman detail transfer.** Segmen, koneksi, validator (ETag), dan riwayat kecepatan
   hanya bisa dilihat sebagai ringkasan.
9. **Layout belum adaptif.** Di tablet/foldable konten akan melebar penuh; belum ada max-width
   atau dua panel (daftar + detail).
10. **Status bar/edge-to-edge** sudah benar, tetapi belum ada splash bermerek (Android 12+).

### Rekomendasi visual konkret (dampak tinggi, usaha rendah)
- Satu CTA per state; hapus FAB saat daftar kosong, atau sebaliknya.
- Ganti teks "Idle" di kartu dengan informasi berguna saat idle: "Storage 42 GB free · 3 files
  today" atau "Queue empty".
- Baris transfer: judul + `host · size · ETA` + progress bar tipis + status chip; aksi utama
  (pause/resume) tetap terlihat, sisanya di long-press/sheet.
- Chip status memakai ikon + teks (bukan warna saja) untuk aksesibilitas.
- Animasi masuk daftar bertahap 30 ms/item, maksimum 6 item, agar terasa hidup tanpa jank.
- Hormati `Settings.Global.ANIMATOR_DURATION_SCALE` untuk semua motion kustom (shader sudah).

---

## 4. Audit fitur (gap terhadap IDM/ADM/1DM)

**Queue/engine**
- Wi-Fi only, charging only, "pause on metered" — belum ada.
- Auto-retry dengan exponential backoff + jitter — belum ada (retry manual).
- Prioritas/urutan antre bisa diubah — hanya `prioritize()` untuk item QUEUED.
- Limit kecepatan per item — hanya global.
- Jadwal: AlarmManager inexact, tanpa constraint jaringan/charger.
- Riwayat/statistik unduhan, ekspor/impor queue — belum ada.

**Browser**
- Tanpa tab, history, bookmark, find-in-page, mode desktop, atau incognito.
- Deteksi media hanya lewat `DownloadListener`; stream HLS yang dimuat via XHR/MSE tidak tertangkap.
- Tidak ada adblock/`shouldInterceptRequest` filtering.
- Cookie diteruskan ke engine (bagus), tetapi tidak ada indikator "login aktif".

**Files**
- Tanpa folder browser, rename, search, sort, atau multi-select.
- Belum ada preview/player media bawaan (Media3) — hanya intent keluar.
- Belum ada "open containing folder".

**Integrasi**
- Share target hanya `text/plain`; tidak ada `ACTION_SEND_MULTIPLE` atau `ACTION_VIEW` http.
- Belum ada widget, Quick Settings tile, atau shortcut "New download".
- Magnet dibuka ke aplikasi lain; tidak ada dukungan torrent (keputusan produk, bukan bug).

---

## 5. Rekomendasi engine

### Tahap 1 — perbaikan di atas fondasi sekarang (1–2 hari)
1. B1, B2, B4, B5, B6, B7, B8, B10.
2. Tambah `NetworkCallback`: pause otomatis saat jaringan hilang, resume saat kembali (dengan
   hormati Wi-Fi only).
3. Retry otomatis 3× dengan backoff 2s/8s/30s + jitter, simpan `retries` dan `nextRetryAt`.
4. Cek ruang kosong + `usableSpace` sebelum `execute()` dan sebelum `save()`.
5. Status `SAVING` + progres copy + notifikasi.

### Tahap 2 — upgrade transport (3–5 hari)
- **OkHttp 5** di belakang interface `HttpClient` yang sudah ada (`probe`, `rangeConnection`,
  `getText`). Keuntungan: HTTP/2 multiplexing, connection pool yang bisa dipakai ulang antar
  range, cancellation via `Call`, interceptor untuk header/retry/log, TLS config eksplisit,
  dukungan proxy. Perkiraan tambahan APK: **±700 KB** setelah R8.
- **WorkManager** untuk jadwal + constraint (`UNMETERED`, `REQUIRES_CHARGING`) dengan
  `CoroutineWorker` + `ForegroundInfo`, menggantikan AlarmManager untuk kasus ini.
- Validasi header (nama token RFC 7230, tolak CRLF) sebelum dikirim/disimpan.

### Tahap 3 — HLS/DASH (1–2 minggu)
- Unduh segmen paralel (4–6 koneksi) dengan penulisan berurutan (buffer per segmen, tulis sesuai
  index) dan progres berbasis byte nyata.
- Dukungan `#EXT-X-MEDIA` audio, `#EXT-X-BYTERANGE`, dan live playlist (window + durasi).
- Remux ke MP4 dengan **Media3 Transformer** agar hasil TS/fMP4 bisa diputar di semua player
  (perkiraan +1,2 MB). Alternatif: biarkan TS dan beri label jelas.
- Evaluasi DASH lewat Media3; torrent hanya jika memang arah produknya (jlibtorrent menambah
  puluhan MB dan beban maintenance besar — **tidak disarankan** untuk sekarang).

### Yang tidak perlu dilakukan
- Menulis ulang engine ke NDK/C++ (libcurl+ffmpeg). Untuk target throughput aplikasi ini,
  perbaikan di atas sudah cukup; biaya APK dan maintenance tidak sepadan.
- Three.js/WebView di dalam aplikasi. Tetap rekomendasi lama: bukan untuk UI native.

---

## 6. Rencana pengukuran (harus dilakukan sebelum menyebut "smooth")

| Metrik | Alat | Target |
|---|---|---|
| Startup dingin | Macrobenchmark / `am start -W` | < 800 ms |
| Jank daftar 200 item | `dumpsys gfxinfo` + Macrobenchmark | < 1% frame > 16 ms |
| Throughput 1 GB, 8 koneksi | file uji lokal + `iperf`/server statis | ≥ 80% bandwidth |
| Resume setelah force-stop | uji manual | 0 byte hilang, checksum sama |
| HLS 30 menit, layar mati | uji manual + battery historian | selesai, < 4%/jam |
| Ukuran APK | `assembleRelease` | < 2,5 MB |
| Aksesibilitas | TalkBack + font 200% | semua aksi terbaca, tidak terpotong |

---

## 7. Urutan kerja yang disarankan

**Sprint 1 — integritas & kejujuran data (P0/P1)**
B1, B2, B4, B5, B6, B7, B8, B10 + label Idle/CTA + filter Paused + cek ruang kosong.

**Sprint 2 — ketahanan & transport**
NetworkCallback + constraint, retry otomatis, OkHttp, WorkManager, status SAVING, detail transfer.

**Sprint 3 — fitur & visual**
Search/sort/batch, preview Media3, HLS audio/paralel/remux, browser tab+sniffing, widget/QS tile,
layout adaptif, audit aksesibilitas, splash.

Setiap sprint ditutup dengan build release + verifikasi tanda tangan + screenshot perangkat,
seperti yang sudah dilakukan pada overhaul visual.
