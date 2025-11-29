package com.dboy.coroutine

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object SpUtils {

    private const val SP_NAME = "app_config"
    private lateinit var preferences: SharedPreferences

    /**
     * 必须在 Application 初始化时调用
     */
    fun init(context: Context) {
        preferences = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    fun putString(key: String, value: String) {
        preferences.edit { putString(key, value) }
    }

    fun getString(key: String, default: String = ""): String {
        return preferences.getString(key, default) ?: default
    }

    fun putBoolean(key: String, value: Boolean) {
        preferences.edit { putBoolean(key, value) }
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return preferences.getBoolean(key, default)
    }

    fun putInt(key: String, value: Int) {
        preferences.edit { putInt(key, value) }
    }

    fun getInt(key: String, default: Int = 0): Int {
        return preferences.getInt(key, default)
    }

    fun putLong(key: String, value: Long) {
        preferences.edit { putLong(key, value) }
    }

    fun getLong(key: String, default: Long = 0L): Long {
        return preferences.getLong(key, default)
    }

    fun remove(key: String) {
        preferences.edit { remove(key) }
    }

    fun clear() {
        preferences.edit { clear() }
    }
}
