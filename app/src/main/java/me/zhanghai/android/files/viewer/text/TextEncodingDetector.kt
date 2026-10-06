package me.zhanghai.android.files.viewer.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Guess the encoding of a text file: a byte order mark wins, then valid UTF-8, and anything else
 * falls back to ISO-8859-1 so that every byte is preserved when the file is saved again.
 */
fun detectTextEncoding(bytes: ByteArray): Charset {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) {
        return StandardCharsets.UTF_8
    }
    if (bytes.size >= 2) {
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return StandardCharsets.UTF_16LE
        }
        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return StandardCharsets.UTF_16BE
        }
    }
    return try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        StandardCharsets.UTF_8
    } catch (e: CharacterCodingException) {
        StandardCharsets.ISO_8859_1
    }
}
