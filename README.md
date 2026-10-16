# IconForge — Android Native Icon Studio

Aplikasi Android native dengan Kotlin dan Android framework (tanpa WebView). Proyek menggunakan Gradle Kotlin DSL, `minSdk 23`, `compileSdk 35`, dan `targetSdk 35`.

## Fitur yang tersedia

- Editor template bentuk **Circle, Rounded square, Squircle, Hexagon, Diamond**.
- Palet warna untuk latar template; piksel di luar bentuk tetap transparan.
- Impor gambar custom dari perangkat menggunakan Android document picker.
- Atur ukuran gambar (25–140%) dan rotasi (0–360°); gambar dipotong mengikuti bentuk ikon.
- Pratinjau checkerboard untuk memperlihatkan transparansi; checkerboard tidak masuk ke PNG.
- Ekspor ikon PNG **512 × 512 px**, RGBA, tanpa background persegi yang tidak diinginkan.
- Deteksi aktivitas launcher yang terlihat oleh Android, lalu pasangkan ikon menggunakan package name dan nama activity yang benar.
- Pemetaan ikon disimpan pada penyimpanan privat aplikasi agar tetap tersedia saat aplikasi dibuka lagi.
- Ekspor ZIP berupa proyek Android Studio yang berisi PNG, `appfilter.xml`, `drawable.xml`, manifest, dan kode tampilan icon pack.

## Build aplikasi IconForge

### Android Studio (disarankan)
1. Buka folder `IconForge` sebagai proyek Android Studio.
2. Pastikan Android SDK Platform 35 dan JDK 17+ tersedia.
3. Lakukan Gradle Sync, lalu pilih **Build > Build APK(s)**.
4. APK debug akan muncul di `app/build/outputs/apk/debug/`.

### Terminal Linux / Termux

Jalankan `sh gradlew assembleDebug` dari folder proyek. Skrip akan memakai Gradle lokal bila ada; jika tidak, skrip mencoba mengunduh Gradle 8.11.1. Android SDK Platform 35 dan `ANDROID_HOME`/`ANDROID_SDK_ROOT` tetap diperlukan. Di Termux, konfigurasi Android SDK bisa lebih rumit daripada di Android Studio.

## Cara menggunakan

1. Buat desain di tab **Editor**.
2. Buka **Pemetaan**, cari aplikasi terpasang, kemudian pilih **Pasangkan ikon editor**.
3. Ulangi untuk aplikasi lain. Tombol **Perbarui ikon** mengganti desain yang dipetakan pada komponen itu.
4. Buka **Ekspor** dan buat ZIP proyek icon pack.
5. Ekstrak ZIP dan buka proyek `IconForge-Launcher-Pack` di Android Studio; build sebagai APK.
6. Instal APK hasil build lalu pilih icon pack melalui menu ikon tema pada launcher yang kompatibel.

## Batasan penting Android

- Android tidak menyediakan izin umum bagi aplikasi biasa untuk mengganti ikon asli aplikasi lain secara langsung. IconForge membuat aset dan paket ikon untuk launcher yang mendukung mekanisme icon pack.
- Auto-resolution mengambil `MAIN` + `LAUNCHER` activities yang dapat dilihat melalui package visibility Android. Aplikasi yang tidak memiliki aktivitas launcher, disembunyikan oleh sistem, atau menggunakan komponen khusus mungkin tidak tercantum.
- Ekspor ZIP adalah **proyek sumber untuk dibangun menjadi APK**, bukan file icon pack yang langsung dapat dipasang.
- Kompatibilitas `appfilter.xml` tidak universal. Launcher, ROM, serta versi launcher tertentu dapat mendukung format dan langkah penerapan yang berbeda.

## Struktur utama

- `app/src/main/java/id/riabz/iconforge/MainActivity.kt` — UI native, render ikon, resolusi package/activity, dan ekspor.
- `app/src/main/AndroidManifest.xml` — launcher activity dan deklarasi package-visibility berbasis intent.
- `gradlew` — bootstrap Gradle ringan untuk terminal; Android SDK tetap diperlukan.
