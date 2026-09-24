package com.jarvis.remote.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {

    private fun encodePixels(content: String): Triple<IntArray, Int, Int> {
        val matrix: BitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 200, 200)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return Triple(pixels, width, height)
    }

    @Test
    fun decode_roundTripsQrText() {
        val content = "opencode://opencode:secret123@192.168.1.5:4096"
        val (pixels, width, height) = encodePixels(content)
        assertEquals(content, QrDecoder.decode(pixels, width, height))
    }

    @Test
    fun decode_returnsNullForGarbageImage() {
        val pixels = IntArray(200 * 200) { 0xFFFFFFFF.toInt() }
        assertNull(QrDecoder.decode(pixels, 200, 200))
    }

    @Test
    fun decode_guardsBadDimensions() {
        assertNull(QrDecoder.decode(IntArray(4) { 0xFF000000.toInt() }, 0, 5))
        assertNull(QrDecoder.decode(IntArray(4) { 0xFF000000.toInt() }, 5, 0))
        assertNull(QrDecoder.decode(ByteArray(0), 10, 10))
    }

    @Test
    fun parseQrQuietly_wrapsNullParse() {
        val emptyProfile = ConnectionProfile("n", "http://localhost:1", "u", "p")
        assertNull(emptyProfile.parseQrQuietly("not a qr"))
        val parsed = emptyProfile.parseQrQuietly("opencode://opencode:pass@box.local")
        assertNotNull(parsed)
        assertEquals("box.local", parsed?.displayHost)
    }
}