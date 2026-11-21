# NusaTunnel 2.2.6

## Peringatan migrasi Android — baca sebelum memasang

v2.2.6 memakai sertifikat signing baru. Android tidak menerima update langsung di atas instalasi sideload v2.2.5. **v2.2.5 tidak memiliki ekspor backup**, jadi backup terenkripsi yang baru tersedia di v2.2.6 tidak dapat mengekspor data dari aplikasi lama. Profil server, kredensial, URL subscription, dan pengaturan lokal lama tidak akan otomatis berpindah.

Sebelum uninstall v2.2.5, pulihkan tautan/kredensial server serta URL subscription dari sumber asal dan catat pengaturan yang ingin dibuat ulang. Jika tidak dapat memperoleh konfigurasi itu, jangan uninstall dulu. Setelah memasang v2.2.6, tambahkan profil dan pengaturan secara manual. Lihat [panduan migrasi](android-signing-rotation-user-notice.md).

Mulai v2.2.6, **Settings → Backup & Restore** menyediakan ekspor/pemulihan backup terenkripsi untuk migrasi versi mendatang. Passphrase minimal 12 karakter; simpan terpisah dari file dan jangan sampai hilang.

## Perubahan

- Splash screen Android baru dan transisi Compose yang lebih halus.
- Backup/restore Android terenkripsi mencakup profil server dan kredensial, subscription, pilihan server, serta pengaturan Android.
- Pembaruan dependensi dan pemeriksaan CI/KVM untuk alur UI dan pemulihan backup.

## Batasan yang tetap berlaku

Rilis ini tidak menyatakan paritas penuh dengan HTTP Custom. Lalu lintas VPN/Xray langsung di jaringan nyata dan perilaku khusus perangkat fisik belum diverifikasi melalui uji terkontrol.
