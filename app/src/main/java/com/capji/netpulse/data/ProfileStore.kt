package com.capji.netpulse.data

import android.content.Context
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.RouterType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved router profiles. Passwords are deliberately NOT stored.
 * To remember passwords later, use Android Keystore + EncryptedSharedPreferences / DataStore with Tink.
 */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("router_profiles", Context.MODE_PRIVATE)

    fun load(): List<RouterProfile> = runCatching {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            RouterProfile(
                id = o.getString("id"),
                name = o.getString("name"),
                type = RouterType.valueOf(o.getString("type")),
                host = o.getString("host"),
                username = o.getString("user"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(profile: RouterProfile): List<RouterProfile> =
        persist(load().filterNot { it.id == profile.id } + profile)

    fun delete(id: String): List<RouterProfile> = persist(load().filterNot { it.id == id })

    fun clear(): List<RouterProfile> = persist(emptyList())

    private fun persist(list: List<RouterProfile>): List<RouterProfile> {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name)
                    .put("host", it.host).put("user", it.username),
            )
        }
        prefs.edit().putString(KEY, a.toString()).apply()
        return list
    }

    private companion object { const val KEY = "list" }
}
