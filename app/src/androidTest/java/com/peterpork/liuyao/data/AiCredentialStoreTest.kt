package com.peterpork.liuyao.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AiCredentialStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = KeystoreAiCredentialStore(context)
    private val file get() = File(context.noBackupFilesDir, "ai-credential-silicon_flow.v1")

    @Test fun saveReplaceDeleteAndCorruptionFailSafely() = runBlocking {
        store.delete(ProviderId.SILICON_FLOW)
        store.save(ProviderId.SILICON_FLOW, "sk-first")
        val firstCiphertext = file.readText()
        assertEquals("sk-first", store.load(ProviderId.SILICON_FLOW))
        assertFalse(firstCiphertext.contains("sk-first"))

        store.save(ProviderId.SILICON_FLOW, "sk-first")
        assertNotEquals(firstCiphertext, file.readText())
        store.save(ProviderId.SILICON_FLOW, "sk-second")
        assertEquals("sk-second", store.load(ProviderId.SILICON_FLOW))

        file.writeText("broken\nvalue")
        assertNull(store.load(ProviderId.SILICON_FLOW))
        store.delete(ProviderId.SILICON_FLOW)
        assertNull(store.load(ProviderId.SILICON_FLOW))
    }

    @Test fun credentialsAreIsolatedByProvider() = runBlocking {
        store.delete(ProviderId.SILICON_FLOW)
        store.delete(ProviderId.DEEPSEEK)
        store.save(ProviderId.SILICON_FLOW, "sk-silicon")
        store.save(ProviderId.DEEPSEEK, "sk-deepseek")
        assertEquals("sk-silicon", store.load(ProviderId.SILICON_FLOW))
        assertEquals("sk-deepseek", store.load(ProviderId.DEEPSEEK))
        store.delete(ProviderId.DEEPSEEK)
        assertEquals("sk-silicon", store.load(ProviderId.SILICON_FLOW))
        assertNull(store.load(ProviderId.DEEPSEEK))
    }
}
