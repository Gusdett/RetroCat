# Retro Cat 🐱 — Tamagotchi 8-bit untuk Android

Hewan peliharaan virtual berupa kucing, bergaya pixel-art 8-bit di atas layar putih-krem.
Tanpa library tambahan (Kotlin + Android SDK saja), tanpa file gambar/suara: semua sprite dan
efek suara chiptune dibuat lewat kode.

## Cara build
1. Buka folder ini di **Android Studio** (Koala atau lebih baru), tunggu Gradle sync.
2. Run di emulator/HP (minSdk 24 / Android 7.0).
Atau lewat terminal: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## Cara bermain
| Tombol | Fungsi |
|---|---|
| FEED  | Beri ikan (+kenyang) |
| PLAY  | Mini-game "tangkap ikan": geser/tap layar untuk menggerakkan kucing (25 detik) |
| SLEEP | Tidurkan / bangunkan kucing (energi pulih saat tidur) |
| CLEAN | Bersihkan kotoran |
| HEAL  | Obati kucing yang sakit |

- Tap kucing untuk mengelusnya. Tap telur untuk mempercepat menetas.
- Ikon speaker di kanan atas = suara on/off.

## Intro (layar judul)
Setiap aplikasi dibuka tampil layar judul **RETRO CAT** dengan kucing yang melompat mengikuti ketukan,
bintang berkedip, dan lagu chiptune orisinal 4 channel (melodi, arpeggio, bass, drum) sepanjang ±26 detik
yang berulang sampai layar disentuh. Ikon speaker di pojok kanan atas mematikan/menyalakan musik.
Lagu ditulis sebagai teks nada di `Song.kt` dan dirender oleh synthesizer kecil (tanpa file audio),
jadi mudah diubah: tempo (`BPM`), akor (`chords`), dan melodi (`lead`).

## Adopsi (layar awal)
Saat pertama dibuka (dan setelah NEW EGG) muncul layar **ADOPT A KITTY**: pilih warna bulu
(oranye, abu-abu, hitam, krem, cokelat), tekan **NAME** untuk memberi nama (maks 8 huruf), lalu **START**.
Jam telur baru berjalan setelah START.

## Notifikasi
Saat aplikasi ditutup, game menghitung kapan kucing akan butuh sesuatu (telur menetas, lapar, kotor, sakit,
kesepian) dan memasang **satu** pengingat pada saat itu (paling cepat 5 menit setelah ditutup).
Pengingat dibatalkan begitu aplikasi dibuka. Android 13+ meminta izin notifikasi saat pertama dibuka.

## Siklus hidup
Telur (1 menit) → Anak kucing (20 menit) → Kucing dewasa.
- Lapar, bosan, lelah turun seiring waktu nyata. Kotoran yang menumpuk >3 menit membuat kucing sakit.
- Lapar 0 selama 20 menit, atau sakit 30 menit tanpa obat → kucing mati (hantu). Tombol NEW EGG memulai ulang.
- Saat aplikasi ditutup, waktu tetap berjalan (setengah kecepatan, maks 3 jam) dan kucing **tidak** mati saat offline.

## Struktur kode (app/src/main/java/com/retrocat/tamagotchi)
- `Pet.kt` — logika & status, simpan/muat
- `GameView.kt` — gambar layar, animasi, sentuhan, mini-game
- `Sprites.kt`, `Font.kt` — pixel art & font 3x5 (digenerate)
- `Notifier.kt` — pengingat (AlarmManager) + penerima notifikasi
- `Song.kt` — lagu intro (partitur + synthesizer)
- `Chip.kt` — synthesizer gelombang kotak untuk efek suara
- Tuning kecepatan ada di `Pet.kt` (konstanta di `companion object` dan di `tick()`).
