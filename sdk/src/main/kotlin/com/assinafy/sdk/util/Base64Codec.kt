package com.assinafy.sdk.util

/**
 * RFC 4648 base64. Hand-rolled because `java.util.Base64` needs API 26 while this SDK supports
 * API 21, and `android.util.Base64` is unavailable to JVM unit tests.
 */
internal object Base64Codec {
    private const val STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** Standard alphabet with `=` padding (§4). */
    fun encode(bytes: ByteArray): String = encode(bytes, STANDARD, pad = true)

    /** URL-safe alphabet without padding (§5). */
    fun encodeUrlNoPadding(bytes: ByteArray): String = encode(bytes, URL_SAFE, pad = false)

    /**
     * Decodes standard or URL-safe base64, with or without padding.
     *
     * @return The decoded bytes, or `null` when [text] is not valid base64.
     */
    fun decode(text: String): ByteArray? {
        val body = text.trimEnd('=')
        if (body.length % 4 == 1) return null
        val out = java.io.ByteArrayOutputStream(body.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (char in body) {
            val value = when (char) {
                '+', '-' -> 62
                '/', '_' -> 63
                else -> STANDARD.indexOf(char).takeIf { it in 0..61 } ?: return null
            }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    private fun encode(bytes: ByteArray, alphabet: String, pad: Boolean): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index < bytes.size) {
            val b0 = bytes[index].toInt() and 0xFF
            val b1 = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xFF else -1
            val b2 = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xFF else -1
            out.append(alphabet[b0 ushr 2])
            out.append(alphabet[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
            if (b1 >= 0) {
                out.append(alphabet[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)])
            } else if (pad) {
                out.append('=')
            }
            if (b2 >= 0) {
                out.append(alphabet[b2 and 0x3F])
            } else if (pad) {
                out.append('=')
            }
            index += 3
        }
        return out.toString()
    }
}
