package com.coolapk.port.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

/**
 * 设备信息 / X-App-Device 生成器
 *
 * 对齐 C# DeviceInfo.ToString()：
 *   "aid; ; ; mac; manufactory; brand; model; build; oaid"
 * 然后 Base64（去 padding）再整体反转字符串。
 */
class DeviceInfo(
    val androidId: String,
    val mac: String,
    val manufactory: String,
    val brand: String,
    val model: String,
    val buildNumber: String,
    val oaid: String,
) {
    fun createDeviceCode(): String = toRawString().toByteArray().let { Base64.encode(it) }.reversed()

    private fun toRawString(): String =
        listOf(androidId, "", "", mac, manufactory, brand, model, buildNumber, oaid).joinToString("; ")

    companion object {
        private const val PREF = "device_info"
        private const val KEY_CODE = "device_code"

        /** 首次运行生成并持久化，之后固定不变（模拟真实设备） */
        fun obtain(context: Context): String {
            val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            sp.getString(KEY_CODE, null)?.let { if (it.isNotBlank()) return it }
            val code = generate(context).createDeviceCode()
            sp.edit().putString(KEY_CODE, code).apply()
            return code
        }

        fun generate(context: Context): DeviceInfo {
            val aid = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull().takeUnless { it.isNullOrBlank() } ?: randomHex(16)
            return DeviceInfo(
                androidId = aid,
                mac = randomMac(),
                manufactory = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                brand = Build.BRAND.replaceFirstChar { it.uppercase() },
                model = Build.MODEL,
                buildNumber = Build.ID.ifBlank { "AP3A.240905.015" },
                oaid = randomOaid(),
            )
        }

        private fun randomHex(length: Int): String {
            val chars = "0123456789abcdef"
            return (1..length).map { chars.random() }.joinToString("")
        }

        private fun randomMac(): String = (1..6).joinToString(":") { randomHex(2) }

        private fun randomOaid(): String {
            val normalized = (randomHex(8) + randomHex(8) + randomHex(16)).take(31)
            val sum = normalized.sumOf { it.digitToInt(16) } % 16
            return normalized + sum.toString(16)
        }

        @Suppress("unused")
        fun randomUuid(): String = UUID.randomUUID().toString()
    }
}