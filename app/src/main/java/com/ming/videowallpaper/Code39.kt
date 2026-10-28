package com.ming.videowallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap

/**
 * Code 39 barcode, the symbology used by Taiwan's e-invoice mobile barcode (手機條碼) carrier.
 * Wide elements are 3x the narrow width.
 */
object Code39 {
    /** e-invoice mobile barcode: "/" followed by 7 characters from 0-9, A-Z, ".", "-", "+". */
    private val CARRIER_REGEX = Regex("^/[0-9A-Z.+\\-]{7}$")

    fun isValidCarrier(code: String): Boolean = CARRIER_REGEX.matches(code)

    // 9 elements per character, alternating bar/space starting with a bar; 1 = wide.
    private val PATTERNS = mapOf(
        '0' to "000110100", '1' to "100100001", '2' to "001100001", '3' to "101100000",
        '4' to "000110001", '5' to "100110000", '6' to "001110000", '7' to "000100101",
        '8' to "100100100", '9' to "001100100", 'A' to "100001001", 'B' to "001001001",
        'C' to "101001000", 'D' to "000011001", 'E' to "100011000", 'F' to "001011000",
        'G' to "000001101", 'H' to "100001100", 'I' to "001001100", 'J' to "000011100",
        'K' to "100000011", 'L' to "001000011", 'M' to "101000010", 'N' to "000010011",
        'O' to "100010010", 'P' to "001010010", 'Q' to "000000111", 'R' to "100000110",
        'S' to "001000110", 'T' to "000010110", 'U' to "110000001", 'V' to "011000001",
        'W' to "111000000", 'X' to "010010001", 'Y' to "110010000", 'Z' to "011010000",
        '-' to "010000101", '.' to "110000100", ' ' to "011000100", '$' to "010101000",
        '/' to "010100010", '+' to "010001010", '%' to "000101010", '*' to "010010100",
    )

    private const val WIDE = 3
    const val QUIET_MODULES = 10

    /** Element widths in narrow modules, alternating bar/space and starting with a bar. */
    private fun elementWidths(text: String): List<Int> {
        val widths = mutableListOf<Int>()
        "*$text*".forEachIndexed { i, ch ->
            val pattern = requireNotNull(PATTERNS[ch]) { "Code 39 cannot encode '$ch'" }
            if (i > 0) widths += 1 // Narrow space between characters.
            pattern.forEach { widths += if (it == '1') WIDE else 1 }
        }
        return widths
    }

    /** Total width in narrow modules, including quiet zones on both sides. */
    fun totalModules(text: String): Int = elementWidths(text).sum() + 2 * QUIET_MODULES

    /** Black bars on white, [moduleWidth] px per narrow module, with quiet zones. */
    fun render(text: String, moduleWidth: Int, height: Int): Bitmap {
        val bitmap = createBitmap(totalModules(text) * moduleWidth, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        var x = QUIET_MODULES * moduleWidth
        elementWidths(text).forEachIndexed { i, w ->
            val px = w * moduleWidth
            if (i % 2 == 0) canvas.drawRect(x.toFloat(), 0f, (x + px).toFloat(), height.toFloat(), paint)
            x += px
        }
        return bitmap
    }
}
