# NusaTunnel 2.2.7

## Peringatan migrasi Android — baca sebelum memasang

Android v2.2.7 memakai sertifikat penandatanganan baru yang diperkenalkan pada v2.2.6 (versionCode 7). Karena itu Android **tidak dapat memperbarui langsung** instalasi sideload v2.2.5 ke v2.2.7. Version 2.2.5 tidak memiliki fitur ekspor backup; backup terenkripsi yang diperkenalkan pada v2.2.6 tidak bisa mengekspor profil atau pengaturan dari aplikasi lama.

Sebelum uninstall v2.2.5, ambil kembali tautan/kredensial profil, URL subscription, dan pengaturan yang diperlukan dari sumber asal. Jika Anda tidak dapat memperoleh konfigurasi itu, **jangan uninstall dulu**. Setelah memasang v2.2.7, tambahkan ulang konfigurasi v2.2.5 secara manual. Lihat [panduan migrasi](android-signing-rotation-user-notice.md).

## Perubahan

- Rilis Android versionCode 8 dengan splash screen dan transisi Compose yang diperbarui.
- Menyediakan backup/restore Android terenkripsi yang mencakup profil dan kredensial, pilihan server, URL subscription, serta pengaturan VPN/aplikasi. Fitur ini diperkenalkan pada lini kode v2.2.6 (versionCode 7) dan pertama kali dipaketkan dalam rilis publik ini (v2.2.7, versionCode 8).
- Backup dienkripsi di perangkat dengan passphrase yang dimasukkan pengguna; passphrase tidak dikirim ke server. Passphrase minimal 12 karakter. Android signing key tidak disertakan dalam backup data aplikasi.
- CI memvalidasi konfigurasi Xray, build Android, dan alur UI/pemulihan backup pada emulator KVM.

## Batasan yang tetap berlaku

Rilis ini tidak menyatakan paritas penuh dengan HTTP Custom. Lalu lintas VPN/Xray langsung di jaringan nyata dan perilaku khusus perangkat fisik belum diverifikasi melalui uji terkontrol.
