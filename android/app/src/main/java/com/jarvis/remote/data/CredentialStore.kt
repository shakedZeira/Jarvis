package com.jarvis.remote.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.jarvis.remote.data.model.JarvisJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class CredentialStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        throw IllegalStateException("Encrypted credential store unavailable", it)
    }

    fun save(profile: ConnectionProfile) {
        val writer = prefs.edit()
            .putString(profileKey(profile.name), JarvisJson.encodeToString(ConnectionProfile.serializer(), profile))
        val names = loadAllNames().toMutableList()
        if (profile.name !in names) names.add(profile.name)
        writer.putString(KEY_PROFILES, JarvisJson.encodeToString(ListSerializer(String.serializer()), names))
        writer.apply()
    }

    fun load(name: String): ConnectionProfile? =
        prefs.getString(profileKey(name), null)?.let { raw ->
            runCatching {
                JarvisJson.decodeFromString(ConnectionProfile.serializer(), raw)
            }.getOrNull()
        }

    fun loadAll(): List<ConnectionProfile> = loadAllNames().mapNotNull(::load)

    fun loadDefault(): ConnectionProfile? = prefs.getString(KEY_DEFAULT, null)?.let(::load)

    fun setDefault(name: String) {
        prefs.edit().putString(KEY_DEFAULT, name).apply()
    }

    fun delete(name: String) {
        prefs.edit()
            .remove(profileKey(name))
            .putString(
                KEY_PROFILES,
                JarvisJson.encodeToString(ListSerializer(String.serializer()), loadAllNames().filterNot { it == name })
            )
            .apply()
        if (prefs.getString(KEY_DEFAULT, null) == name) {
            prefs.edit().remove(KEY_DEFAULT).apply()
        }
    }

    private fun loadAllNames(): List<String> =
        prefs.getString(KEY_PROFILES, null)?.let { raw ->
            runCatching {
                JarvisJson.decodeFromString(ListSerializer(String.serializer()), raw)
            }.getOrNull()
        } ?: emptyList()

    companion object {
        private const val PREFS_NAME = "jarvis_creds"
        private const val KEY_DEFAULT = "default_profile"
        private const val KEY_PROFILES = "profiles"

        private fun profileKey(name: String) = "profile:$name"

        @JvmStatic
        fun forContext(context: Context): CredentialStore = CredentialStore(context)
    }
}
