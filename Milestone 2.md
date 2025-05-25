# Tugas Besar 2 - Android IF3210 Pengembangan Aplikasi Piranti Bergerak

## ✨ Daftar Isi

- [Deskripsi Aplikasi Web](#-deskripsi-aplikasi-web)
- [Cara Menjalankan Aplikasi](#-cara-menjalankan-aplikasi)
- [Daftar Library](#-daftar-library)
- [Pembagian Kerja Kelompok](#-pembagian-kerja-kelompok)
- [Total Jam Persiapan dan Pengerjaan](#-total-jam-persiapan-dan-pengerjaan)

## ✨ Deskripsi Aplikasi Web

Untuk Milestone 2 ini, terdapat beberapa fitur tambahan pada aplikasi. Beberapa diantaranya yaitu ada _online song_ dari server backend asisten, _sound capsule_, _media player_ dari notifikasi, dan fitur _share song_ dengan link serta _QR code_.

## ✨ Cara Menjalankan Aplikasi

Install `app.apk`

## ✨ Daftar Library

1. AndroidX Core

   - androidx.core\:core-ktx:1.12.0
   - androidx.activity\:activity-compose:1.8.2
   - androidx.lifecycle\:lifecycle-runtime-ktx:2.7.0
   - androidx.lifecycle\:lifecycle-viewmodel-ktx:2.7.0
   - androidx.localbroadcastmanager\:localbroadcastmanager:1.1.0
   - androidx.security\:security-crypto:1.0.0
   - androidx.core\:core-splashscreen:1.0.1

2. Jetpack Compose

   - androidx.compose\:compose-bom:2024.02.00
   - androidx.compose.ui\:ui
   - androidx.compose.ui\:ui-graphics
   - androidx.compose.ui\:ui-tooling-preview
   - androidx.compose.material\:material-icons-extended:1.5.4
   - androidx.compose.material3\:material3
   - androidx.navigation\:navigation-compose:2.7.7
   - androidx.lifecycle\:lifecycle-viewmodel-compose:2.7.0
   - androidx.lifecycle\:lifecycle-runtime-compose:2.7.0

3. Image Loading

   - io.coil-kt\:coil-compose:2.2.2

4. Networking

   - com.squareup.retrofit2\:retrofit:2.9.0
   - com.squareup.retrofit2\:converter-gson:2.9.0
   - com.squareup.okhttp3\:logging-interceptor:4.12.0

5. Coroutines

   - org.jetbrains.kotlinx\:kotlinx-coroutines-android:1.7.3

6. Testing

   - junit\:junit:4.13.2
   - androidx.test.ext\:junit:1.1.5
   - androidx.test.espresso\:espresso-core:3.5.1

7. Compose Testing

   - androidx.compose\:compose-bom:2024.02.00
   - androidx.compose.ui\:ui-test-junit4

8. Debug

   - androidx.compose.ui\:ui-tooling
   - androidx.compose.ui\:ui-test-manifest

9. Database (Room)

   - androidx.room\:room-runtime:2.6.1
   - androidx.room\:room-ktx:2.6.1
   - androidx.room\:room-compiler:2.6.1 *(kapt)*

10. Location Service

    - org.osmdroid\:osmdroid-android:6.1.18
    - com.google.android.gms\:play-services-location:21.2.0

11. QR Code

    - com.google.zxing\:core:3.5.3
    - com.journeyapps\:zxing-android-embedded:4.3.0

## ✨ Pembagian Kerja Kelompok

| Fitur                              | Kontributor                                    |
| ---------------------------------- | ---------------------------------------------- |
| Online Songs                       | [13522147](https://github.com/Nerggg)          |
| Download Online Songs              | [13522147](https://github.com/Nerggg)          |
| Sound Capsule (Analytics) & Export | [13522126](https://github.com/rizqikapratamaa) |
| Notification Controls              | [13522161](https://github.com/akmalrmn)        |
| AudioRouting and Output Device     | [13522126](https://github.com/rizqikapratamaa) |
| Share Songs via URL                | [13522147](https://github.com/Nerggg)          |
| Share Songs via QR                 | [13522147](https://github.com/Nerggg)          |
| Halaman Responsive                 | [13522147](https://github.com/Nerggg)          |
| Edit Profile                       | [13522161](https://github.com/akmalrmn)        |
| Rekomendasi Lagu                   | [13522161](https://github.com/akmalrmn)        |

## ✨ Total Jam Persiapan dan Pengerjaan

| Kontributor                                    | Total Jam |
| ---------------------------------------------- | --------- |
| [13522126](https://github.com/rizqikapratamaa) | 60        |
| [13522147](https://github.com/Nerggg)          | 60        |
| [13522161](https://github.com/akmalrmn)        | 60        |
