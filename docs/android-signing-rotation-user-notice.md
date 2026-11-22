# Penting: migrasi Android v2.2.5 ke v2.2.6+

## Untuk pengguna Android sideload

**Jangan uninstall v2.2.5 sebelum menyiapkan ulang konfigurasi Anda.** Sertifikat penandatanganan baru mulai digunakan pada versi 2.2.6 (versionCode 7) dan tetap digunakan oleh v2.2.7 (versionCode 8). Android tidak mengizinkan APK dengan sertifikat baru dipasang sebagai update langsung di atas instalasi sideload v2.2.5.

v2.2.5 belum memiliki fitur ekspor backup. Backup terenkripsi mulai tersedia pada lini kode v2.2.6 dan pertama kali dipaketkan dalam rilis publik v2.2.7; fitur ini **tidak dapat** dipakai untuk mengekspor data dari v2.2.5. Profil server, kredensial, pilihan server, URL subscription, dan pengaturan yang hanya tersimpan di aplikasi lama tidak akan otomatis dipindahkan.

Sebelum uninstall:

1. Simpan tautan atau kredensial asli semua profil server dari sumber/provider Anda.
2. Simpan URL subscription dari sumber aslinya dan catat pengaturan khusus yang ingin dipertahankan.
3. Jika Anda tidak dapat memperoleh kembali konfigurasi tersebut, **jangan uninstall v2.2.5 dulu**; tunggu sampai Anda memiliki konfigurasi yang diperlukan.
4. Setelah siap, uninstall v2.2.5, pasang v2.2.7, lalu impor/masukkan kembali profil dan pengaturan secara manual.

Mulai v2.2.6, menu **Settings → Backup & Restore** dapat membuat dan memulihkan berkas `.ntbackup` terenkripsi; fitur tersebut tersedia di v2.2.7. Backup mencakup profil, kredensial, pilihan server, URL subscription, serta pengaturan VPN/aplikasi. Enkripsi dilakukan di perangkat dengan passphrase yang dimasukkan pengguna; passphrase tidak dikirim ke server. Passphrase minimal 12 karakter, simpan terpisah dari berkas, dan jangan kehilangan keduanya. Backup data aplikasi tidak mencakup Android signing key.

Jangan mengandalkan pemulihan otomatis Android untuk mempertahankan data lintas perubahan sertifikat. Panduan ini tidak menjanjikan pemindahan otomatis dari v2.2.5.

## English summary

Sideloaded Android v2.2.5 cannot be updated in place to v2.2.7: the new signing certificate was introduced in v2.2.6 (versionCode 7) and is still used by v2.2.7 (versionCode 8). Version 2.2.5 has no export feature, so encrypted backup introduced in the v2.2.6 code line (first publicly packaged in v2.2.7) cannot export data from that old installation. Before uninstalling, recover and save your original server links/credentials, selected server details, subscription URL, and any settings you need. If you cannot reconstruct them, do not uninstall yet. After installing v2.2.7, re-add profiles and settings manually.

Encrypted `.ntbackup` export/restore begins in the v2.2.6 code line and is first included in the public v2.2.7 release. It covers profiles, credentials, selected server, subscription URL, and VPN/app settings. Encryption is performed on-device using the passphrase entered by the user; the passphrase is not sent to a server. Passphrases must be at least 12 characters. The app-data backup does not include the Android signing key.
