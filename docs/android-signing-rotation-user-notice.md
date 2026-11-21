# Penting: migrasi Android v2.2.5 ke v2.2.6

## Untuk pengguna Android sideload

**Jangan uninstall v2.2.5 sebelum menyiapkan ulang konfigurasi Anda.** v2.2.6 ditandatangani dengan sertifikat baru. Android tidak mengizinkan APK bertanda tangan baru dipasang sebagai update langsung di atas instalasi v2.2.5.

v2.2.5 belum memiliki fitur ekspor backup. Karena itu backup terenkripsi yang tersedia mulai v2.2.6 **tidak dapat** dipakai untuk mengekspor data dari v2.2.5. Profil server, kredensial, pilihan server, URL subscription, dan pengaturan yang hanya tersimpan di aplikasi lama tidak akan otomatis dipindahkan.

Sebelum uninstall:

1. Simpan tautan atau kredensial asli semua profil server dari sumber/provider Anda.
2. Simpan URL subscription dari sumber aslinya dan catat pengaturan khusus yang ingin dipertahankan.
3. Jika Anda tidak dapat memperoleh kembali konfigurasi tersebut, **jangan uninstall v2.2.5 dulu**; tunggu sampai Anda memiliki konfigurasi yang diperlukan.
4. Setelah siap, uninstall v2.2.5, pasang v2.2.6, lalu impor/masukkan kembali profil dan pengaturan secara manual.

Mulai v2.2.6, menu **Settings → Backup & Restore** dapat membuat/memulihkan berkas `.ntbackup` terenkripsi yang mencakup profil, kredensial, subscription, dan pengaturan Android. Pilih passphrase minimal 12 karakter, simpan passphrase terpisah dari berkas, dan jangan kehilangan keduanya. Backup ini ditujukan untuk migrasi ke versi mendatang; backup tidak dapat dibuka tanpa passphrase.

Jangan mengandalkan pemulihan otomatis Android untuk mempertahankan data lintas perubahan sertifikat. Panduan ini tidak menjanjikan pemindahan otomatis dari v2.2.5.

## English summary

Sideloaded Android v2.2.5 cannot be updated in place to v2.2.6 because the signing certificate has changed. Version 2.2.5 has no export feature, so the new encrypted backup cannot export data from that old installation. Before uninstalling, recover and save your original server links/credentials, subscription URL, and any settings you need. If you cannot reconstruct them, do not uninstall yet. After installing v2.2.6, re-add profiles and settings manually. Encrypted `.ntbackup` export/restore is available from v2.2.6 onward for future migrations.
