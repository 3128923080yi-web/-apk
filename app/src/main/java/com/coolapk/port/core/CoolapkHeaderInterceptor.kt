package com.coolapk.port.core

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 统一注入酷安 API 必需请求头。
 *
 * ⚠️ 重点：`X-Requested-With: XMLHttpRequest` 缺失时，网关会返回
 *    {"status":1000,"message":"请求方式错误"}（实测），这是移植时最容易踩的坑。
 */
class CoolapkHeaderInterceptor(
    private val deviceCode: String,
    private val cookieProvider: () -> String? = { null },
    private val darkModeProvider: () -> Boolean = { false },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val versionCode = API_VERSION_CODE
        val builder = chain.request().newBuilder()
            .header("X-App-Token", TokenCreator.getToken(deviceCode, versionCode))
            .header("X-Sdk-Int", android.os.Build.VERSION.SDK_INT.toString())
            .header("X-Sdk-Locale", "zh-CN")
            .header("X-App-Mode", "universal")
            .header("X-App-Channel", "coolapk")
            .header("X-App-Id", APP_ID)
            .header("X-App-Device", deviceCode)
            .header("X-App-Version", API_VERSION)
            .header("X-App-Code", versionCode.toString())
            .header("X-Api-Supported", versionCode.toString())
            .header("X-Api-Version", API_VERSION.substringBefore('.'))
            .header("X-Dark-Mode", if (darkModeProvider()) "1" else "0")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json")
            .header("User-Agent", defaultUserAgent())

        cookieProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        return chain.proceed(builder.build())
    }

    companion object {
        fun defaultUserAgent(): String {
            val model = android.os.Build.MODEL ?: "Phone"
            val release = android.os.Build.VERSION.RELEASE
            val buildId = android.os.Build.ID
            return "Dalvik/2.1.0 (Linux; U; Android $release; $model Build/$buildId) " +
                "(#Build; ${android.os.Build.MANUFACTURER}; $model; $buildId; $release) " +
                "+CoolMarket/$API_VERSION-$API_VERSION_CODE-universal"
        }
    }
}