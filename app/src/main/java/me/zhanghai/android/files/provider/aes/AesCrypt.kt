/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.aes

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of the AES Crypt file format, stream format version 2.
 *
 * A file starts with the ASCII header "AES", the stream format version and a reserved byte,
 * followed by the extension blocks, which are terminated by a zero length. Then comes the 16 byte
 * initialization vector that protects the session block, the 48 byte encrypted session block
 * holding the initialization vector and the key that actually encrypt the payload, the HMAC of
 * the session block, the encrypted payload, the size of the payload modulo 16 and finally the
 * HMAC of the encrypted payload.
 *
 * The password is converted to UTF-16LE and hashed with SHA-256 8192 times, seeded with the
 * initialization vector, to derive the key that protects the session block.
 */
class AesCrypt(password: String) {
    private val password: ByteArray = password.toByteArray(Charsets.UTF_16LE)

    /**
     * Encrypts [input] and writes the result to [output].
     */
    @Throws(AesCryptException::class)
    fun encrypt(input: InputStream, output: OutputStream) {
        val iv1 = randomBytes(IV_LENGTH)
        val aesKey1 = deriveKey(iv1, password)
        val iv2 = randomBytes(IV_LENGTH)
        val aesKey2 = randomBytes(KEY_LENGTH)
        val session = encryptSession(aesKey1, iv1, iv2, aesKey2)
        output.write(HEADER)
        output.write(VERSION)
        output.write(RESERVED)
        // No extension blocks.
        output.write(0)
        output.write(0)
        output.write(iv1)
        output.write(session)
        output.write(hmac(aesKey1, session))
        val cipher = createCipher(Cipher.ENCRYPT_MODE, aesKey2, iv2)
        val mac = createHmac(aesKey2)
        val buffer = ByteArray(BUFFER_SIZE)
        // The last block is padded with zeros, its real size is stored in the file.
        val pending = ByteArray(BLOCK_SIZE)
        var pendingSize = 0
        var totalSize = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            totalSize += read
            var offset = 0
            if (pendingSize > 0) {
                val count = minOf(BLOCK_SIZE - pendingSize, read)
                System.arraycopy(buffer, 0, pending, pendingSize, count)
                pendingSize += count
                offset = count
                if (pendingSize == BLOCK_SIZE) {
                    writeEncrypted(cipher, mac, output, pending)
                    pendingSize = 0
                }
            }
            while (offset + BLOCK_SIZE <= read) {
                writeEncrypted(cipher, mac, output, buffer, offset, BLOCK_SIZE)
                offset += BLOCK_SIZE
            }
            if (offset < read) {
                pending.fill(0)
                System.arraycopy(buffer, offset, pending, 0, read - offset)
                pendingSize = read - offset
            }
        }
        if (pendingSize > 0) {
            writeEncrypted(cipher, mac, output, pending)
        }
        val encrypted = cipher.doFinal()
        mac.update(encrypted)
        output.write(encrypted)
        output.write((totalSize % BLOCK_SIZE).toInt())
        output.write(mac.doFinal())
    }

    /**
     * Decrypts [input] and writes the result to [output].
     *
     * The payload is written before it is authenticated, so the caller should discard the output
     * if an exception is thrown.
     */
    @Throws(AesCryptException::class)
    fun decrypt(input: InputStream, output: OutputStream) {
        val header = ByteArray(HEADER.size + 2)
        readFully(input, header)
        for (i in HEADER.indices) {
            if (header[i] != HEADER[i]) {
                throw AesCryptException("The file is not an encrypted file")
            }
        }
        val version = header[HEADER.size].toInt() and 0xFF
        if (version !in 1..2) {
            throw AesCryptException("Unsupported encrypted file version: $version")
        }
        if (version >= 2) {
            skipExtensions(input)
        }
        val iv1 = ByteArray(IV_LENGTH)
        readFully(input, iv1)
        val aesKey1 = deriveKey(iv1, password)
        val session = ByteArray(SESSION_LENGTH)
        readFully(input, session)
        val expectedHmac = ByteArray(HMAC_LENGTH)
        readFully(input, expectedHmac)
        if (!MessageDigest.isEqual(expectedHmac, hmac(aesKey1, session))) {
            throw AesCryptException("The password is wrong or the file is corrupted")
        }
        val cipher = createCipher(Cipher.DECRYPT_MODE, aesKey1, iv1)
        val sessionPlainText = cipher.doFinal(session)
        val iv2 = sessionPlainText.copyOfRange(0, IV_LENGTH)
        val aesKey2 = sessionPlainText.copyOfRange(IV_LENGTH, SESSION_LENGTH)
        val payloadCipher = createCipher(Cipher.DECRYPT_MODE, aesKey2, iv2)
        val mac = createHmac(aesKey2)
        // The last block of the payload is kept back, because its real size is only known after
        // the payload has been read.
        val held = ByteArray(HELD_LENGTH)
        var heldSize = 0
        val buffer = ByteArray(BUFFER_SIZE + HELD_LENGTH)
        while (true) {
            System.arraycopy(held, 0, buffer, 0, heldSize)
            val read = input.read(buffer, heldSize, BUFFER_SIZE)
            if (read < 0) {
                break
            }
            val totalSize = heldSize + read
            val keepSize = minOf(HELD_LENGTH, totalSize)
            val processSize = totalSize - keepSize
            if (processSize > 0) {
                val encrypted = buffer.copyOfRange(0, processSize)
                mac.update(encrypted)
                output.write(payloadCipher.update(encrypted))
            }
            System.arraycopy(buffer, processSize, held, 0, keepSize)
            heldSize = keepSize
        }
        if (heldSize == HMAC_LENGTH + 1) {
            // The payload is empty.
            val expectedPayloadHmac = held.copyOfRange(1, 1 + HMAC_LENGTH)
            if (!MessageDigest.isEqual(expectedPayloadHmac, mac.doFinal())) {
                throw AesCryptException("The password is wrong or the file is corrupted")
            }
            output.write(payloadCipher.doFinal())
            return
        }
        if (heldSize != HELD_LENGTH) {
            throw AesCryptException("The file is truncated")
        }
        val lastBlock = held.copyOfRange(0, BLOCK_SIZE)
        mac.update(lastBlock)
        val lastBlockSize = held[BLOCK_SIZE].toInt() and 0x0F
        val plainText = payloadCipher.doFinal(lastBlock)
        output.write(plainText, 0, if (lastBlockSize == 0) BLOCK_SIZE else lastBlockSize)
        val expectedPayloadHmac = held.copyOfRange(BLOCK_SIZE + 1, HELD_LENGTH)
        if (!MessageDigest.isEqual(expectedPayloadHmac, mac.doFinal())) {
            throw AesCryptException("The password is wrong or the file is corrupted")
        }
    }

    /**
     * Thrown when a file cannot be encrypted or decrypted.
     */
    class AesCryptException(message: String, cause: Throwable? = null) : IOException(message, cause)

    companion object {
        /**
         * The file name extension used for encrypted files.
         */
        const val FILE_EXTENSION = ".aes"

        private val HEADER = "AES".toByteArray(Charsets.US_ASCII)

        private const val VERSION = 2

        private const val RESERVED = 0

        private const val BLOCK_SIZE = 16

        private const val IV_LENGTH = 16

        private const val KEY_LENGTH = 32

        private const val SESSION_LENGTH = IV_LENGTH + KEY_LENGTH

        private const val HMAC_LENGTH = 32

        private const val BUFFER_SIZE = 64 * 1024

        /**
         * The number of bytes kept back from the end of the file, which are the last block of the
         * payload, the size of the payload modulo 16 and the HMAC of the payload.
         */
        private const val HELD_LENGTH = BLOCK_SIZE + 1 + HMAC_LENGTH

        private const val KEY_ITERATIONS = 8192

        private val random = SecureRandom()

        private fun randomBytes(length: Int): ByteArray =
            ByteArray(length).also { random.nextBytes(it) }

        /**
         * Derives the key that protects the session block by hashing the initialization vector
         * and the password with SHA-256 [KEY_ITERATIONS] times.
         */
        private fun deriveKey(iv: ByteArray, password: ByteArray): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            val key = ByteArray(32)
            System.arraycopy(iv, 0, key, 0, IV_LENGTH)
            for (i in 0 until KEY_ITERATIONS) {
                digest.reset()
                digest.update(key)
                digest.update(password)
                digest.digest().copyInto(key)
            }
            return key
        }

        private fun encryptSession(
            aesKey1: ByteArray,
            iv1: ByteArray,
            iv2: ByteArray,
            aesKey2: ByteArray
        ): ByteArray {
            val cipher = createCipher(Cipher.ENCRYPT_MODE, aesKey1, iv1)
            val session = ByteArray(SESSION_LENGTH)
            System.arraycopy(iv2, 0, session, 0, IV_LENGTH)
            System.arraycopy(aesKey2, 0, session, IV_LENGTH, KEY_LENGTH)
            return cipher.doFinal(session)
        }

        private fun writeEncrypted(
            cipher: Cipher,
            mac: Mac,
            output: OutputStream,
            data: ByteArray,
            offset: Int = 0,
            length: Int = data.size
        ) {
            val encrypted = cipher.update(data, offset, length)
            mac.update(encrypted)
            output.write(encrypted)
        }

        private fun createCipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
            try {
                Cipher.getInstance("AES/CBC/NoPadding").apply {
                    init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                }
            } catch (e: GeneralSecurityException) {
                throw AesCryptException("Failed to initialize the cipher", e)
            }

        private fun createHmac(key: ByteArray): Mac =
            try {
                Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            } catch (e: GeneralSecurityException) {
                throw AesCryptException("Failed to initialize the authentication", e)
            }

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            createHmac(key).apply { update(data) }.doFinal()

        private fun skipExtensions(input: InputStream) {
            while (true) {
                val length = ByteArray(2)
                readFully(input, length)
                val size = ((length[0].toInt() and 0xFF) shl 8) or (length[1].toInt() and 0xFF)
                if (size == 0) {
                    return
                }
                var remaining = size
                val buffer = ByteArray(minOf(remaining, BUFFER_SIZE))
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(remaining, buffer.size))
                    if (read < 0) {
                        throw AesCryptException("The file is truncated")
                    }
                    remaining -= read
                }
            }
        }

        @Throws(IOException::class)
        private fun readFully(input: InputStream, buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read < 0) {
                    throw EOFException("The file is truncated")
                }
                offset += read
            }
        }
    }
}
