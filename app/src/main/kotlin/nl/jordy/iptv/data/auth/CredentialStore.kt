package nl.jordy.iptv.data.auth

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import nl.jordy.iptv.data.xtream.XtreamCredentials
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xtream login, encrypted at rest with AES-256-GCM. The Tink keyset is itself wrapped by a key in
 * the Android Keystore, so the credentials file is useless off the device.
 */
@Singleton
class CredentialStore @Inject constructor(@ApplicationContext context: Context) {

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "iptv_keyset", "iptv_crypto")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://iptv_credentials_key")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val store: DataStore<XtreamCredentials?> by lazy {
        DataStoreFactory.create(EncryptedSerializer { aead }) { context.dataStoreFile("credentials.bin") }
    }

    val credentials: Flow<XtreamCredentials?> get() = store.data

    suspend fun current(): XtreamCredentials? = store.data.first()

    suspend fun save(c: XtreamCredentials) {
        store.updateData { c }
    }

    suspend fun clear() {
        store.updateData { null }
    }

    private class EncryptedSerializer(private val aead: () -> Aead) : Serializer<XtreamCredentials?> {
        override val defaultValue: XtreamCredentials? = null

        override suspend fun readFrom(input: InputStream): XtreamCredentials? {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) return null
            return try {
                Json.decodeFromString(XtreamCredentials.serializer(), String(aead().decrypt(bytes, AAD), Charsets.UTF_8))
            } catch (e: GeneralSecurityException) {
                throw CorruptionException("Cannot decrypt credentials", e)
            }
        }

        override suspend fun writeTo(t: XtreamCredentials?, output: OutputStream) {
            if (t == null) return
            output.write(aead().encrypt(Json.encodeToString(XtreamCredentials.serializer(), t).toByteArray(), AAD))
        }
    }

    private companion object {
        val AAD = "xtream-credentials".toByteArray()
    }
}
