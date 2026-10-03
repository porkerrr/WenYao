package com.peterpork.liuyao.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ProviderId { SILICON_FLOW, DEEPSEEK }

interface AiCredentialStore {
    suspend fun save(providerId: ProviderId, credential: String)
    suspend fun load(providerId: ProviderId): String?
    suspend fun delete(providerId: ProviderId)
    suspend fun hasCredential(providerId: ProviderId): Boolean = load(providerId) != null
}

class KeystoreAiCredentialStore(private val context: Context) : AiCredentialStore {
    override suspend fun save(providerId: ProviderId, credential: String) = withContext(Dispatchers.IO) {
        require(credential.isNotBlank()) { "API Key 不能为空" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(aad(providerId))
        val encrypted = cipher.doFinal(credential.trim().toByteArray(Charsets.UTF_8))
        val payload = (Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "\n" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)).toByteArray(Charsets.UTF_8)
        val atomicFile = AtomicFile(credentialFile(providerId))
        val output = atomicFile.startWrite()
        try {
            output.write(payload)
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    override suspend fun load(providerId: ProviderId): String? = withContext(Dispatchers.IO) {
        val file = credentialFile(providerId)
        if (!file.isFile) return@withContext null
        runCatching {
            val parts = file.readLines(Charsets.UTF_8)
            require(parts.size == 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                existingSecretKey() ?: error("凭据加密密钥不存在"),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            cipher.updateAAD(aad(providerId))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    override suspend fun delete(providerId: ProviderId) = withContext(Dispatchers.IO) {
        credentialFile(providerId).delete()
        Unit
    }

    private fun credentialFile(providerId: ProviderId) =
        File(context.noBackupFilesDir, "ai-credential-${providerId.name.lowercase()}.v1")

    private fun aad(providerId: ProviderId) =
        "wenyao|${providerId.name.lowercase()}|credential-v1".toByteArray(Charsets.UTF_8)

    private fun existingSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun secretKey(): SecretKey = existingSecretKey() ?: KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        KEYSTORE
    ).run {
        init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "wenyao_ai_credentials_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
