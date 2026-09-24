package com.jarvis.remote.data

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

object QrDecoder {

    fun decode(bytes: ByteArray, width: Int, height: Int): String? {
        val expected = width * height
        if (width <= 0 || height <= 0 || bytes.size < expected) return null
        val perPixel = when {
            bytes.size >= expected * 4 -> 4
            bytes.size >= expected * 3 -> 3
            else -> return null
        }
        val pixels = IntArray(expected)
        for (i in 0 until expected) {
            val o = i * perPixel
            pixels[i] = if (perPixel == 4) {
                ((bytes[o].toInt() and 0xFF) shl 24) or
                    ((bytes[o + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[o + 2].toInt() and 0xFF) shl 8) or
                    (bytes[o + 3].toInt() and 0xFF)
            } else {
                0xFF000000.toInt() or
                    ((bytes[o].toInt() and 0xFF) shl 16) or
                    ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                    (bytes[o + 2].toInt() and 0xFF)
            }
        }
        return decode(pixels, width, height)
    }

    fun decode(pixels: IntArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        return try {
            val source = RGBLuminanceSource(width, height, pixels)
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        } catch (e: Exception) {
            null
        }
    }

    fun decode(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return decode(pixels, w, h)
    }
}

fun ConnectionProfile.parseQrQuietly(content: String): ConnectionProfile? = parseQr(content)