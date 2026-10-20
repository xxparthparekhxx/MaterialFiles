/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.aes

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AesCryptTest {
    @Test
    fun decryptsFileEncryptedByReferenceImplementation() {
        for (vector in VECTORS) {
            val output = ByteArrayOutputStream()
            AesCrypt(vector.password).decrypt(ByteArrayInputStream(vector.encrypted), output)
            assertArrayEquals(vector.plainText, output.toByteArray())
        }
    }

    @Test
    fun encryptedFileHasExpectedStructure() {
        for (plainText in PLAIN_TEXTS) {
            val output = ByteArrayOutputStream()
            AesCrypt("apples").encrypt(ByteArrayInputStream(plainText), output)
            val encrypted = output.toByteArray()
            assertEquals("AES", String(encrypted, 0, 3, Charsets.US_ASCII))
            assertEquals(2, encrypted[3].toInt())
            assertEquals(0, encrypted[4].toInt())
            // No extension blocks.
            assertEquals(0, encrypted[5].toInt())
            assertEquals(0, encrypted[6].toInt())
            // The size of the payload modulo 16.
            assertEquals(plainText.size % 16, encrypted[encrypted.size - 33].toInt())
        }
    }

    @Test
    fun roundTripsPlainText() {
        for (plainText in PLAIN_TEXTS) {
            val encrypted = ByteArrayOutputStream()
            AesCrypt("apples").encrypt(ByteArrayInputStream(plainText), encrypted)
            val decrypted = ByteArrayOutputStream()
            AesCrypt("apples").decrypt(ByteArrayInputStream(encrypted.toByteArray()), decrypted)
            assertArrayEquals(plainText, decrypted.toByteArray())
        }
    }

    @Test
    fun roundTripsWithDifferentPasswords() {
        for (password in PASSWORDS) {
            for (plainText in PLAIN_TEXTS) {
                val encrypted = ByteArrayOutputStream()
                AesCrypt(password).encrypt(ByteArrayInputStream(plainText), encrypted)
                val decrypted = ByteArrayOutputStream()
                AesCrypt(password).decrypt(
                    ByteArrayInputStream(encrypted.toByteArray()), decrypted
                )
                assertArrayEquals(plainText, decrypted.toByteArray())
            }
        }
    }

    @Test
    fun failsWithWrongPassword() {
        val encrypted = ByteArrayOutputStream()
        AesCrypt("apples").encrypt(ByteArrayInputStream("Hello, World!".toByteArray()), encrypted)
        assertThrows(AesCrypt.AesCryptException::class.java) {
            AesCrypt("oranges").decrypt(
                ByteArrayInputStream(encrypted.toByteArray()), ByteArrayOutputStream()
            )
        }
    }

    @Test
    fun failsWithCorruptedFile() {
        val encrypted = ByteArrayOutputStream()
        AesCrypt("apples").encrypt(ByteArrayInputStream("Hello, World!".toByteArray()), encrypted)
        val corrupted = encrypted.toByteArray().also {
            it[it.size - 1] = (it[it.size - 1] + 1).toByte()
        }
        assertThrows(AesCrypt.AesCryptException::class.java) {
            AesCrypt("apples").decrypt(ByteArrayInputStream(corrupted), ByteArrayOutputStream())
        }
    }

    @Test
    fun failsWithPlainFile() {
        assertThrows(AesCrypt.AesCryptException::class.java) {
            AesCrypt("apples").decrypt(
                ByteArrayInputStream("Hello, World!".toByteArray()), ByteArrayOutputStream()
            )
        }
    }

    private class Vector(
        val password: String,
        val plainText: ByteArray,
        val encrypted: ByteArray
    )

    companion object {
        private val PLAIN_TEXTS = listOf(
            ByteArray(0),
            "Hello, World!".toByteArray(),
            ByteArray(16) { 'A'.code.toByte() },
            ByteArray(100) { 'B'.code.toByte() },
            "secret data\n".repeat(10).toByteArray()
        )

        private val PASSWORDS = listOf("", "apples", "p@ssw0rd with spaces & symbols!")

        // The encrypted files were produced by the reference implementation of the AES Crypt file
        // format, which encodes the password as UTF-16LE.
        private val VECTORS = listOf(
            Vector(
                "apples",
                "Hello, World!".toByteArray(),
                hexToBytes(
                        "41455302000017435245415445445f425900616573637279707420312e300080000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                                                + "00000000000000000000d37c8a1defba344fc9635f63c392485a5c6a6b9af23b360c0641da80"
                                                + "eb7c20bfead26e467c63f99317b8fb6034ecad6e3126d276b60f37a2ef472a6619b3728827fb"
                                                + "44ea74d3ef06fd7097c72941c1e0983f6886dd8ee37559e7fe30c0637490f981a701cba58996"
                                                + "5773f0f47bc0ac4f0d757971ecce768cd166c329a14bf34227b21e85481cf3ed643e3de762ea"
                                                + "c77ee4")
            ),
            Vector(
                "apples",
                ByteArray(100) { 'B'.code.toByte() },
                hexToBytes(
                        "41455302000017435245415445445f425900616573637279707420312e300080000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                                                + "00000000000000000000320273095b22570253d878d67aa9c56513df831ece7e15183f8b0b07"
                                                + "35e30a024e8ee6190240de5daf49610f49810fa5acc57c040b9dc27f7c9396f84bfb96b5d9a9"
                                                + "e2099d2b51477870b971a532d2db672b0cb3bd5b40618c5e13abe3e3a1efd43c3e1f684afbe7"
                                                + "a6e43d9059c28cc7fa2bc301c81b8467a37f2bd23ff31adbc9988ad54fd46beef0375b2e2daa"
                                                + "0d5d1962e8637d7228f6aedd0635396531956e3ffbf0770efd7844c7b21cf6c54646b1d01552"
                                                + "0610eec835e15981f9833f3581554042e1b30223ca32128f2b00974504ebc374f19a67b3e304"
                                                + "7c54bcfac77308e009f094e715cd5200cadfe5e3cd1119")
            ),
            Vector(
                "",
                ByteArray(0),
                hexToBytes(
                        "41455302000017435245415445445f425900616573637279707420312e300080000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                                                + "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                                                + "00000000000000000000228a735c01cb9a1f5249644c414aa27486a7ea5ca4c59830b6128117"
                                                + "56f766abaff31cde765c63c97c1a262ced87d1ec5f2965ccb691c3de24810092372f0a20ccaf"
                                                + "8f5fd31dac5e9d16fc0de3817cb1aa8ee1655063a35566bd0bef405dc596000786c5c6966aed"
                                                + "8fc3aa818b3d1eb8537becfe529571b4a87c04569b5c38ccbf")
            ),
        )

        private fun hexToBytes(hex: String): ByteArray =
            ByteArray(hex.length / 2) {
                hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            }
    }
}
