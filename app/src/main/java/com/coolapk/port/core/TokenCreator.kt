package com.coolapk.port.core

import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * Coolapk X-App-Token 生成器（Kotlin 移植版）
 *
 * 端口来源: CoolapkUWP/Common/TokenCreator.cs (TokenV3)
 * 已在 PC 上用 Python 等价实现验证通过（api.coolapk.com 返回 200）。
 *
 * 关键点:
 *  1. BLOB 是混淆后的模板库，按时间戳+版本号取 128 字符偏移切片；
 *  2. 密码 = md5(base64("com.coolapk.market&{internal}&{md5(deviceCode)}&{ts}&{versionCode}"))；
 *  3. salt 由 "{ts:x}/{md5(input)}" 的 Base64 前 22 字符构成，末位用 & 0x30 掩码修正；
 *  4. bcrypt cost = 4，前缀 $2y$，结果再做 Base64 并加 "v3" 前缀。
 */
object TokenCreator {

    private const val BLOB =
        "TTBUOFQsQ0ElLUMkWDEjQSEsUyEmLDNAUy1DPFYuIy0iMUM5IzBUJFctMykhMUQ1JS1DOSUxMzhQLVMwV00sIyhSMTQsWTFDRFEw" +
        "Q0RVMSQkUS0jKSMtJDhYLVQkUjEzKSQsM0BVLTQwUi00NSMsM2BXLVMlJC1DNFlNLCQ0WTEzLSQxQ0EhLEMlIjEzKFcwMz0lLiNE" +
        "VzEjPSYuNDkkLFMwVixTKFktQzhXLFMsWC4jLSYtU2BTTTA0LFEuMzhTLjMhIjFDMFMsI0BVLCQkUCwzJSIsIzBSMEM8UCxELSIt" +
        "I2BQLUQoUy0jLFMsIyUkMFQwVk0tRCxYMTMlJC40NSEwU0RQLFMhIi0kNSMwU2BQLjNgUywjOFEsNCxTLEMwWCxULSEtIyRTLDNB" +
        "Iy00JFZNMUMsUDBEJFgtNC0kLDQkWC1DMFMxRCkjLVM0VixTMFgxIy0mLVMxJS0zJFQxM2BQLSQ4WTBDMFMuI0UhTSwzPFgtRDBR" +
        "MDNEUS0jOFYuJDklMSQ0UzAzNSMwRDkkMTMkVSxUMFQtRCxYMTNFIyxELFYsIzUhLiQ0V00xNCxVLCM0Vy4zJFUxNCxTLFQ4Vy4j" +
        "YFYxJDUjLUMwUzFDPFIsM0ElLDMsVDA0OSMuNCkmLEQkVixEMSJNLTMoUiwzLFctU0RQLiQwVTFDJSUwUykjMUMhIy0zISUsI0Ej" +
        "MTMwUS1UNFcxRDElLUQtJS4kNFIsIzhZTTBTYFEwQyRYLSMlIS40LFgsVDRZLUQoWTBDQFYtRCxWLjNEVDBDMFktNDUlLFQ4VS1U" +
        "JFgwMzRWLDQ4UU0wQy0mLUQ4WS1TMSMsUyUiMUNAUSxULFYuI0RULiMsWDFELFMxI2BQLVM0WS4kOFcwM0RXLVMoWC0jKFExLCM0" +
        "VixTNFAtRDRWLUQlJC1TJFAwNCxgYA"

    /** bcrypt 使用的 base64 字母表（注意开头是 "./"，与标准 Base64 不同） */
    private const val BCRYPT_ALPHABET = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private val cached = HashMap<String, Pair<Long, String>>()
    private const val TTL_MS = 60_000L

    /** 带缓存的取 token（同一 deviceCode 60 秒内复用，避免每个请求都跑 bcrypt） */
    @Synchronized
    fun getToken(deviceCode: String, versionCode: Int = API_VERSION_CODE): String {
        val now = System.currentTimeMillis()
        cached[deviceCode]?.let { (expire, token) ->
            if (expire > now) return token
        }
        val token = createTokenV3(deviceCode, versionCode)
        cached[deviceCode] = (now + TTL_MS) to token
        return token
    }

    /** 生成 X-App-Token（v3） */
    fun createTokenV3(deviceCode: String, versionCode: Int = API_VERSION_CODE): String {
        val ts = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        val offset = (((ts + versionCode) % 100) * 4 + 128).toInt()
        val internal = Base64.decode(BLOB.substring(offset, offset + 128)).decodeToString()

        val input = "com.coolapk.market&$internal&${deviceCode.md5()}&$ts&$versionCode"
        val password = input.toByteArray().base64().toByteArray().md5()

        val saltBody = "${ts.toString(16)}/${input.md5()}"
        val salt22 = saltBody.toByteArray().base64().take(22)
        val idx = BCRYPT_ALPHABET.indexOf(salt22.last())
        require(idx >= 0) { "invalid bcrypt salt char" }
        val saltText = salt22.dropLast(1) + BCRYPT_ALPHABET[idx and 0x30]

        val saltBytes = decodeBcryptSalt(saltText)
        val hashBytes = BCrypt.with(BCrypt.Version.VERSION_2Y)
            .hash(4, saltBytes, password.toByteArray())
        // at.favre 输出 $2y$04$... 形式，整体 Base64 后再拼 "v3" 前缀
        return "v3" + Base64.encode(hashBytes)
    }

    /** 把 22 个字符的 bcrypt salt 文本解码为 16 字节 */
    private fun decodeBcryptSalt(salt: String): ByteArray {
        val out = ByteArray(16)
        var bytePos = 0
        var i = 0
        while (i + 1 < salt.length && bytePos < 16) {
            val c1 = BCRYPT_ALPHABET.indexOf(salt[i])
            val c2 = BCRYPT_ALPHABET.indexOf(salt[i + 1])
            require(c1 >= 0 && c2 >= 0) { "invalid bcrypt salt" }
            out[bytePos++] = (((c1 shl 2) or (c2 shr 4)) and 0xFF).toByte()
            if (bytePos < 16 && i + 2 < salt.length) {
                val c3 = BCRYPT_ALPHABET.indexOf(salt[i + 2])
                out[bytePos++] = (((c2 shl 4) or (c3 shr 2)) and 0xFF).toByte()
            }
            if (bytePos < 16 && i + 3 < salt.length) {
                val c3 = BCRYPT_ALPHABET.indexOf(salt[i + 2])
                val c4 = BCRYPT_ALPHABET.indexOf(salt[i + 3])
                out[bytePos++] = (((c3 shl 6) or c4) and 0xFF).toByte()
            }
            i += 4
        }
        return out
    }

    private fun String.md5(): String =
        MessageDigest.getInstance("MD5").digest(toByteArray()).joinToString("") { "%02x".format(it) }

    private fun ByteArray.md5(): String =
        MessageDigest.getInstance("MD5").digest(this).joinToString("") { "%02x".format(it) }

    private fun ByteArray.base64(): String = Base64.encode(this)
}