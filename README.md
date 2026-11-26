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

## Siklus hidup
Telur (1 menit) → Anak kucing (20 menit) → Kucing dewasa.
- Lapar, bosan, lelah turun seiring waktu nyata. Kotoran yang menumpuk >3 menit membuat kucing sakit.
- Lapar 0 selama 20 menit, atau sakit 30 menit tanpa obat → kucing mati (hantu). Tombol NEW EGG memulai ulang.
- Saat aplikasi ditutup, waktu tetap berjalan (setengah kecepatan, maks 3 jam) dan kucing **tidak** mati saat offline.

## Struktur kode (app/src/main/java/com/retrocat/tamagotchi)
- `Pet.kt` — logika & status, simpan/muat
- `GameView.kt` — gambar layar, animasi, sentuhan, mini-game
- `Sprites.kt`, `Font.kt` — pixel art & font 3x5 (digenerate)
- `Chip.kt` — synthesizer gelombang kotak untuk efek suara
- Tuning kecepatan ada di `Pet.kt` (konstanta di `companion object` dan di `tick()`).
