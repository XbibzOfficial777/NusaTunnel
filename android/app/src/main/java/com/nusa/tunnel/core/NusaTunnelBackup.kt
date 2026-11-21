package com.nusa.tunnel.core

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Public metadata only; encrypted file contents and passphrases are never logged. */
class NusaTunnelBackupException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

@Serializable
data class NusaTunnelBackupPayload(
    val formatVersion: Int,
    val exportedAtEpochMillis: Long,
    val serverStore: ServerStoreSnapshot,
    val settings: TunnelSettingsBackup,
)

@Serializable
private data class EncryptedBackupEnvelope(
    val format: String,
    val version: Int,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val ciphertext: String,
)

/** Password-encrypted, portable backup for server credentials, subscription, and app settings. */
object NusaTunnelBackup {
    const val FILE_SUFFIX = ".ntbackup"
    const val MIN_PASSPHRASE_LENGTH = 12
    const val MAX_PASSPHRASE_LENGTH = 128
    const val FORMAT_VERSION = 1

    private const val FORMAT_ID = "com.nusatunnel.encrypted-backup"
    private const val KDF_ID = "PBKDF2WithHmacSHA256"
    private const val KDF_ITERATIONS = 240_000
    private const val MIN_KDF_ITERATIONS = 150_000
    private const val MAX_KDF_ITERATIONS = 500_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val MAX_FILE_BYTES = 64 * 1024 * 1024
    private const val MAX_PROFILES = 10_000
    private const val MAX_PROFILE_TEXT = 65_536
    private const val DEFAULT_PACKAGE = "com.nusa.tunnel"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
    }
    private val random = SecureRandom()

    suspend fun export(
        context: Context,
        serverStore: ServerStore,
        output: OutputStream,
        passphrase: CharArray,
    ) {
        requirePassphrase(passphrase)
        val payload = NusaTunnelBackupPayload(
            formatVersion = FORMAT_VERSION,
            exportedAtEpochMillis = System.currentTimeMillis(),
            serverStore = serverStore.backupSnapshot(),
            settings = TunnelSettings.backupSnapshot(context),
        )
        validatePayload(payload, context.applicationContext.packageName)
        val bytes = encryptPayload(payload, passphrase, context.applicationContext.packageName)
        if (bytes.size > MAX_FILE_BYTES) {
            throw NusaTunnelBackupException("Backup terlalu besar untuk disimpan.")
        }
        output.write(bytes)
        output.flush()
    }

    suspend fun restore(
        context: Context,
        serverStore: ServerStore,
        input: InputStream,
        passphrase: CharArray,
    ) {
        requirePassphrase(passphrase)
        val encoded = readBounded(input)
        val payload = decryptPayload(encoded, passphrase, context.applicationContext.packageName)

        // Validate everything before mutating either persistent store.
        validatePayload(payload, context.applicationContext.packageName)
        val previousServers = serverStore.backupSnapshot()
        val previousSettings = TunnelSettings.backupSnapshot(context)

        try {
            serverStore.restoreSnapshot(payload.serverStore)
            if (!TunnelSettings.restoreBackupSnapshot(context, payload.settings)) {
                throw NusaTunnelBackupException("Pengaturan tidak dapat disimpan; data lama dipulihkan.")
            }
        } catch (failure: Throwable) {
            val rollbackFailed = withContext(NonCancellable) {
                val serverRollbackFailed = runCatching {
                    serverStore.restoreSnapshot(previousServers)
                }.isFailure
                val settingsRollbackFailed = runCatching {
                    !TunnelSettings.restoreBackupSnapshot(context, previousSettings)
                }.getOrDefault(true)
                serverRollbackFailed || settingsRollbackFailed
            }
            if (rollbackFailed) {
                throw NusaTunnelBackupException(
                    "Pemulihan terhenti dan pengembalian otomatis tidak lengkap. Jangan tutup aplikasi; hubungi dukungan.",
                    failure,
                )
            }
            if (failure is CancellationException) throw failure
            if (failure is NusaTunnelBackupException) throw failure
            throw NusaTunnelBackupException("Pemulihan gagal; data sebelumnya sudah dikembalikan.", failure)
        }
    }

    internal fun encryptPayload(
        payload: NusaTunnelBackupPayload,
        passphrase: CharArray,
        ownerPackage: String = DEFAULT_PACKAGE,
    ): ByteArray {
        requirePassphrase(passphrase)
        validatePayload(payload, ownerPackage)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(passphrase, salt, KDF_ITERATIONS),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(aad(FORMAT_VERSION))
        val plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val ciphertext = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val envelope = EncryptedBackupEnvelope(
            format = FORMAT_ID,
            version = FORMAT_VERSION,
            kdf = KDF_ID,
            iterations = KDF_ITERATIONS,
            salt = Base64.getEncoder().encodeToString(salt),
            iv = Base64.getEncoder().encodeToString(iv),
            ciphertext = Base64.getEncoder().encodeToString(ciphertext),
        )
        return json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
    }

    internal fun decryptPayload(
        bytes: ByteArray,
        passphrase: CharArray,
        ownerPackage: String = DEFAULT_PACKAGE,
    ): NusaTunnelBackupPayload {
        requirePassphrase(passphrase)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) {
            throw NusaTunnelBackupException("Ukuran berkas backup tidak valid.")
        }
        val envelope = try {
            json.decodeFromString<EncryptedBackupEnvelope>(bytes.toString(Charsets.UTF_8))
        } catch (failure: SerializationException) {
            throw NusaTunnelBackupException("Format berkas backup tidak dikenali atau rusak.", failure)
        }
        if (envelope.format != FORMAT_ID || envelope.version != FORMAT_VERSION || envelope.kdf != KDF_ID) {
            throw NusaTunnelBackupException("Versi atau format backup tidak didukung.")
        }
        if (envelope.iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) {
            throw NusaTunnelBackupException("Parameter keamanan backup tidak valid.")
        }
        val salt = decodeBase64(envelope.salt, SALT_BYTES, SALT_BYTES)
        val iv = decodeBase64(envelope.iv, IV_BYTES, IV_BYTES)
        val ciphertext = decodeBase64(envelope.ciphertext, GCM_TAG_BITS / 8 + 1, MAX_FILE_BYTES)
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(passphrase, salt, envelope.iterations),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.updateAAD(aad(envelope.version))
            cipher.doFinal(ciphertext)
        } catch (failure: AEADBadTagException) {
            throw NusaTunnelBackupException("Passphrase salah atau berkas backup telah berubah.", failure)
        } catch (failure: GeneralSecurityException) {
            throw NusaTunnelBackupException("Berkas backup tidak dapat dibuka pada perangkat ini.", failure)
        }
        val payload = try {
            json.decodeFromString<NusaTunnelBackupPayload>(plaintext.toString(Charsets.UTF_8))
        } catch (failure: SerializationException) {
            throw NusaTunnelBackupException("Isi backup tidak valid.", failure)
        } finally {
            plaintext.fill(0)
        }
        validatePayload(payload, ownerPackage)
        return payload
    }

    private fun validatePayload(payload: NusaTunnelBackupPayload, ownerPackage: String) {
        if (payload.formatVersion != FORMAT_VERSION || payload.exportedAtEpochMillis <= 0L) {
            throw NusaTunnelBackupException("Versi isi backup tidak didukung.")
        }
        val snapshot = payload.serverStore
        if (snapshot.servers.size > MAX_PROFILES) {
            throw NusaTunnelBackupException("Backup berisi terlalu banyak profil server.")
        }
        val ids = HashSet<String>(snapshot.servers.size)
        snapshot.servers.forEach { profile ->
            if (profile.id.isBlank() || profile.id.length > 256 || !ids.add(profile.id) ||
                profile.remarks.length > MAX_PROFILE_TEXT || profile.host.isBlank() ||
                profile.host.length > 2_048 || profile.port !in 1..65_535 ||
                profile.secret.length > MAX_PROFILE_TEXT || profile.encryption.length > 2_048 ||
                profile.network.length > 256 || profile.streamSecurity.length > 256 ||
                profile.realityPublicKey.length > 4_096 || profile.realityShortId.length > 4_096 ||
                profile.realitySpiderX.length > 4_096 || profile.sni.length > 4_096 ||
                profile.alpn.length > 4_096 || profile.fingerprint.length > 256 ||
                profile.flow.length > 2_048 || profile.wsPath.length > 8_192 ||
                profile.wsHost.length > 4_096 || profile.xhttpMode.length > 256 ||
                profile.xhttpExtra.length > MAX_PROFILE_TEXT || profile.grpcServiceName.length > 4_096 ||
                profile.serviceName.length > 4_096 || profile.subTag.length > 4_096 ||
                profile.latencyMs < -1
            ) {
                throw NusaTunnelBackupException("Salah satu profil server di backup tidak valid.")
            }
        }
        if (snapshot.selectedId != null && snapshot.selectedId !in ids) {
            throw NusaTunnelBackupException("Server terpilih di backup tidak ditemukan.")
        }
        val subscription = snapshot.subscriptionUrl
        if (subscription != null && subscription.length > 4_096) {
            throw NusaTunnelBackupException("URL subscription di backup terlalu panjang.")
        }
        if (!TunnelSettings.isValidBackupSnapshot(payload.settings, ownerPackage)) {
            throw NusaTunnelBackupException("Salah satu pengaturan di backup tidak valid.")
        }
    }

    private fun requirePassphrase(passphrase: CharArray) {
        if (passphrase.size !in MIN_PASSPHRASE_LENGTH..MAX_PASSPHRASE_LENGTH) {
            throw NusaTunnelBackupException(
                "Passphrase harus berisi $MIN_PASSPHRASE_LENGTH–$MAX_PASSPHRASE_LENGTH karakter.",
            )
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        return try {
            val keyBytes = SecretKeyFactory.getInstance(KDF_ID).generateSecret(spec).encoded
            try {
                SecretKeySpec(keyBytes, "AES")
            } finally {
                keyBytes.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun aad(version: Int): ByteArray = "$FORMAT_ID:$version".toByteArray(Charsets.UTF_8)

    private fun decodeBase64(value: String, minimum: Int, maximum: Int): ByteArray {
        val decoded = runCatching { Base64.getDecoder().decode(value) }.getOrNull()
            ?: throw NusaTunnelBackupException("Berkas backup memiliki data yang rusak.")
        if (decoded.size !in minimum..maximum) {
            throw NusaTunnelBackupException("Berkas backup memiliki ukuran data yang tidak valid.")
        }
        return decoded
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_FILE_BYTES) {
                throw NusaTunnelBackupException("Berkas backup melebihi batas ukuran.")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
