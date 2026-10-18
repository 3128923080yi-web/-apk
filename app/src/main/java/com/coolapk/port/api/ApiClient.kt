package com.coolapk.port.api

import android.content.Context
import com.coolapk.port.core.BASE_URL
import com.coolapk.port.core.CoolapkHeaderInterceptor
import com.coolapk.port.core.DeviceInfo
import com.google.gson.GsonBuilder
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络层装配（对齐 CoolapkUWP/Helpers/NetworkHelper.cs）：
 *  - 统一 header 拦截器（token / device / UA）
 *  - 持久化 Cookie（登录后 cookie 用于所有请求）
 */
object ApiClient {

    private const val PREF = "cookies"

    lateinit var api: CoolapkApi
        private set

    private lateinit var cookieStore: PersistentCookieJar

    fun init(context: Context) {
        val deviceCode = DeviceInfo.obtain(context)
        cookieStore = PersistentCookieJar(context.applicationContext)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .cookieJar(cookieStore)
            .addInterceptor(CoolapkHeaderInterceptor(deviceCode, cookieProvider = { cookieStore.cookieHeader() }))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .build()
            .create(CoolapkApi::class.java)
    }

    fun cookieHeader(): String? = cookieStore.cookieHeader()

    fun saveCookie(raw: String) = cookieStore.saveRaw(raw)

    fun clearCookies() = cookieStore.clear()

    /** 极简持久化 CookieJar：只保留 name=value，够用且不依赖第三方库 */
    class PersistentCookieJar(context: Context) : CookieJar {
        private val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        private val cookies = HashMap<String, String>()

        init {
            sp.getString("jar", "")?.split("\n")
                ?.filter { it.contains("=") }
                ?.forEach { line ->
                    val name = line.substringBefore("=")
                    val value = line.substringAfter("=")
                    cookies[name] = value
                }
        }

        fun cookieHeader(): String? =
            if (cookies.isEmpty()) null else cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        fun saveRaw(raw: String) {
            raw.split(";").forEach { part ->
                val pair = part.trim()
                if (pair.contains("=") && !pair.startsWith("path") && !pair.startsWith("domain")) {
                    cookies[pair.substringBefore("=")] = pair.substringAfter("=")
                }
            }
            persist()
        }

        fun clear() {
            cookies.clear()
            persist()
        }

        private fun persist() {
            sp.edit().putString("jar", cookies.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
        }

        override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
            cookieList.forEach { cookies[it.name] = it.value }
            persist()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList() // 由拦截器统一附加整串 Cookie
    }
}