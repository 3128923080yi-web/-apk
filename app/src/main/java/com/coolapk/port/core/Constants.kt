package com.coolapk.port.core

import android.util.Base64 as AndroidBase64

/** 当前模拟的酷安客户端版本（与 C# APIVersions.V16 一致） */
const val API_VERSION = "16.6.4"
const val API_VERSION_CODE = 2609291
const val APP_ID = "com.coolapk.market"
const val BASE_URL = "https://api.coolapk.com/"
const val BASE_URL_2 = "https://api2.coolapk.com/"
const val WEB_URL = "https://www.coolapk.com/"

/** Base64 工具：默认去掉 '=' 填充（对齐 C# DataHelper.GetBase64） */
object Base64 {
    fun encode(bytes: ByteArray): String =
        AndroidBase64.encodeToString(bytes, AndroidBase64.NO_WRAP).replace("=", "")

    fun encode(text: String): String = encode(text.toByteArray())

    fun decode(text: String): ByteArray {
        val pad = (4 - text.length % 4) % 4
        val fixed = if (pad == 0) text else text + "=".repeat(pad)
        return AndroidBase64.decode(fixed, AndroidBase64.DEFAULT)
    }
}