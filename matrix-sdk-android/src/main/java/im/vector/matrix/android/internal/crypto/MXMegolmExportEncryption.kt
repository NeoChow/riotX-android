/*
 * Copyright 2017 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.matrix.android.internal.crypto

import android.text.TextUtils
import android.util.Base64
import im.vector.matrix.android.internal.extensions.toUnsignedInt
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import kotlin.experimental.xor

/**
 * Utility class to import/export the crypto data
 */
object MXMegolmExportEncryption {
    private const val HEADER_LINE = "-----BEGIN MEGOLM SESSION DATA-----"
    private const val TRAILER_LINE = "-----END MEGOLM SESSION DATA-----"
    // we split into lines before base64ing, because encodeBase64 doesn't deal
    // terribly well with large arrays.
    private const val LINE_LENGTH = 72 * 4 / 3

    // default iteration count to export the e2e keys
    const val DEFAULT_ITERATION_COUNT = 500000

    /**
     * Extract the AES key from the deriveKeys result.
     *
     * @param keyBits the deriveKeys result.
     * @return the AES key
     */
    private fun getAesKey(keyBits: ByteArray): ByteArray {
        return Arrays.copyOfRange(keyBits, 0, 32)
    }

    /**
     * Extract the Hmac key from the deriveKeys result.
     *
     * @param keyBits the deriveKeys result.
     * @return the Hmac key.
     */
    private fun getHmacKey(keyBits: ByteArray): ByteArray {
        return Arrays.copyOfRange(keyBits, 32, keyBits.size)
    }

    /**
     * Decrypt a megolm key file
     *
     * @param data     the data to decrypt
     * @param password the password.
     * @return the decrypted output.
     * @throws Exception the failure reason
     */
    @Throws(Exception::class)
    fun decryptMegolmKeyFile(data: ByteArray, password: String): String {
        val body = unpackMegolmKeyFile(data)

        // check we have a version byte
        if (null == body || body.size == 0) {
            Timber.e("## decryptMegolmKeyFile() : Invalid file: too short")
            throw Exception("Invalid file: too short")
        }

        val version = body[0]
        if (version.toInt() != 1) {
            Timber.e("## decryptMegolmKeyFile() : Invalid file: too short")
            throw Exception("Unsupported version")
        }

        val ciphertextLength = body.size - (1 + 16 + 16 + 4 + 32)
        if (ciphertextLength < 0) {
            throw Exception("Invalid file: too short")
        }

        if (TextUtils.isEmpty(password)) {
            throw Exception("Empty password is not supported")
        }

        val salt = Arrays.copyOfRange(body, 1, 1 + 16)
        val iv = Arrays.copyOfRange(body, 17, 17 + 16)
        val iterations =
                (body[33].toUnsignedInt() shl 24) or (body[34].toUnsignedInt() shl 16) or (body[35].toUnsignedInt() shl 8) or body[36].toUnsignedInt()
        val ciphertext = Arrays.copyOfRange(body, 37, 37 + ciphertextLength)
        val hmac = Arrays.copyOfRange(body, body.size - 32, body.size)

        val deriveKey = deriveKeys(salt, iterations, password)

        val toVerify = Arrays.copyOfRange(body, 0, body.size - 32)

        val macKey = SecretKeySpec(getHmacKey(deriveKey), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        val digest = mac.doFinal(toVerify)

        if (!Arrays.equals(hmac, digest)) {
            Timber.e("## decryptMegolmKeyFile() : Authentication check failed: incorrect password?")
            throw Exception("Authentication check failed: incorrect password?")
        }

        val decryptCipher = Cipher.getInstance("AES/CTR/NoPadding")

        val secretKeySpec = SecretKeySpec(getAesKey(deriveKey), "AES")
        val ivParameterSpec = IvParameterSpec(iv)
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

        val outStream = ByteArrayOutputStream()
        outStream.write(decryptCipher.update(ciphertext))
        outStream.write(decryptCipher.doFinal())

        val decodedString = String(outStream.toByteArray(), Charset.defaultCharset())
        outStream.close()

        return decodedString
    }

    /**
     * Encrypt a string into the megolm export format.
     *
     * @param data       the data to encrypt.
     * @param password   the password
     * @param kdf_rounds the iteration count
     * @return the encrypted data
     * @throws Exception the failure reason
     */
    @Throws(Exception::class)
    @JvmOverloads
    fun encryptMegolmKeyFile(data: String, password: String, kdf_rounds: Int = DEFAULT_ITERATION_COUNT): ByteArray {
        if (TextUtils.isEmpty(password)) {
            throw Exception("Empty password is not supported")
        }

        val secureRandom = SecureRandom()

        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(16)
        secureRandom.nextBytes(iv)

        // clear bit 63 of the salt to stop us hitting the 64-bit counter boundary
        // (which would mean we wouldn't be able to decrypt on Android). The loss
        // of a single bit of salt is a price we have to pay.
        iv[9] = iv[9] and 0x7f

        val deriveKey = deriveKeys(salt, kdf_rounds, password)

        val decryptCipher = Cipher.getInstance("AES/CTR/NoPadding")

        val secretKeySpec = SecretKeySpec(getAesKey(deriveKey), "AES")
        val ivParameterSpec = IvParameterSpec(iv)
        decryptCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)

        val outStream = ByteArrayOutputStream()
        outStream.write(decryptCipher.update(data.toByteArray(charset("UTF-8"))))
        outStream.write(decryptCipher.doFinal())

        val cipherArray = outStream.toByteArray()
        val bodyLength = 1 + salt.size + iv.size + 4 + cipherArray.size + 32

        val resultBuffer = ByteArray(bodyLength)
        var idx = 0
        resultBuffer[idx++] = 1 // version

        System.arraycopy(salt, 0, resultBuffer, idx, salt.size)
        idx += salt.size

        System.arraycopy(iv, 0, resultBuffer, idx, iv.size)
        idx += iv.size

        resultBuffer[idx++] = (kdf_rounds shr 24 and 0xff).toByte()
        resultBuffer[idx++] = (kdf_rounds shr 16 and 0xff).toByte()
        resultBuffer[idx++] = (kdf_rounds shr 8 and 0xff).toByte()
        resultBuffer[idx++] = (kdf_rounds and 0xff).toByte()

        System.arraycopy(cipherArray, 0, resultBuffer, idx, cipherArray.size)
        idx += cipherArray.size

        val toSign = Arrays.copyOfRange(resultBuffer, 0, idx)

        val macKey = SecretKeySpec(getHmacKey(deriveKey), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        val digest = mac.doFinal(toSign)
        System.arraycopy(digest, 0, resultBuffer, idx, digest.size)

        return packMegolmKeyFile(resultBuffer)
    }

    /**
     * Unbase64 an ascii-armoured megolm key file
     * Strips the header and trailer lines, and unbase64s the content
     *
     * @param data the input data
     * @return unbase64ed content
     */
    @Throws(Exception::class)
    private fun unpackMegolmKeyFile(data: ByteArray): ByteArray? {
        val fileStr = String(data, Charset.defaultCharset())

        // look for the start line
        var lineStart = 0

        while (true) {
            val lineEnd = fileStr.indexOf('\n', lineStart)

            if (lineEnd < 0) {
                Timber.e("## unpackMegolmKeyFile() : Header line not found")
                throw Exception("Header line not found")
            }

            val line = fileStr.substring(lineStart, lineEnd).trim { it <= ' ' }

            // start the next line after the newline
            lineStart = lineEnd + 1

            if (TextUtils.equals(line, HEADER_LINE)) {
                break
            }
        }

        val dataStart = lineStart

        // look for the end line
        while (true) {
            val lineEnd = fileStr.indexOf('\n', lineStart)
            val line: String

            if (lineEnd < 0) {
                line = fileStr.substring(lineStart).trim { it <= ' ' }
            } else {
                line = fileStr.substring(lineStart, lineEnd).trim { it <= ' ' }
            }

            if (TextUtils.equals(line, TRAILER_LINE)) {
                break
            }

            if (lineEnd < 0) {
                Timber.e("## unpackMegolmKeyFile() : Trailer line not found")
                throw Exception("Trailer line not found")
            }

            // start the next line after the newline
            lineStart = lineEnd + 1
        }

        val dataEnd = lineStart

        // Receiving side
        return Base64.decode(fileStr.substring(dataStart, dataEnd), Base64.DEFAULT)
    }

    /**
     * Pack the megolm data.
     *
     * @param data the data to pack.
     * @return the packed data
     * @throws Exception the failure reason.
     */
    @Throws(Exception::class)
    private fun packMegolmKeyFile(data: ByteArray): ByteArray {
        val nLines = (data.size + LINE_LENGTH - 1) / LINE_LENGTH

        val outStream = ByteArrayOutputStream()
        outStream.write(HEADER_LINE.toByteArray())

        var o = 0

        for (i in 1..nLines) {
            outStream.write("\n".toByteArray())

            val len = Math.min(LINE_LENGTH, data.size - o)
            outStream.write(Base64.encode(data, o, len, Base64.DEFAULT))
            o += LINE_LENGTH
        }

        outStream.write("\n".toByteArray())
        outStream.write(TRAILER_LINE.toByteArray())
        outStream.write("\n".toByteArray())

        return outStream.toByteArray()
    }

    /**
     * Derive the AES and HMAC-SHA-256 keys for the file
     *
     * @param salt       salt for pbkdf
     * @param iterations number of pbkdf iterations
     * @param password   password
     * @return the derived keys
     */
    @Throws(Exception::class)
    private fun deriveKeys(salt: ByteArray, iterations: Int, password: String): ByteArray {
        val t0 = System.currentTimeMillis()

        // based on https://en.wikipedia.org/wiki/PBKDF2 algorithm
        // it is simpler than the generic algorithm because the expected key length is equal to the mac key length.
        // noticed as dklen/hlen
        val prf = Mac.getInstance("HmacSHA512")
        prf.init(SecretKeySpec(password.toByteArray(charset("UTF-8")), "HmacSHA512"))

        // 512 bits key length
        val key = ByteArray(64)
        val Uc = ByteArray(64)

        // U1 = PRF(Password, Salt || INT_32_BE(i))
        prf.update(salt)
        val int32BE = ByteArray(4)
        Arrays.fill(int32BE, 0.toByte())
        int32BE[3] = 1.toByte()
        prf.update(int32BE)
        prf.doFinal(Uc, 0)

        // copy to the key
        System.arraycopy(Uc, 0, key, 0, Uc.size)

        for (index in 2..iterations) {
            // Uc = PRF(Password, Uc-1)
            prf.update(Uc)
            prf.doFinal(Uc, 0)

            // F(Password, Salt, c, i) = U1 ^ U2 ^ ... ^ Uc
            for (byteIndex in Uc.indices) {
                key[byteIndex] = key[byteIndex] xor Uc[byteIndex]
            }
        }

        Timber.v("## deriveKeys() : " + iterations + " in " + (System.currentTimeMillis() - t0) + " ms")

        return key
    }
}
