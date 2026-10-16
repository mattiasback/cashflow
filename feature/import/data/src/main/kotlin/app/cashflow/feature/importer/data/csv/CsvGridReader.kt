package app.cashflow.feature.importer.data.csv

import app.cashflow.feature.importer.data.xls.Cell
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Reads CSV into the same cell grid as the `.xls` reader: UTF-8 with or without BOM, or
 * Windows-1252; `;` or `,` separators; quoted fields. Numbers stay text (decimal comma),
 * the profiles parse them.
 */
object CsvGridReader {

    /** The separator the reader would use for these bytes. */
    fun separatorOf(bytes: ByteArray): Char = detectSeparator(decode(bytes).lineSequence().filter { it.isNotBlank() }.take(5).toList())

    fun read(bytes: ByteArray): List<List<Cell?>> {
        val text = decode(bytes)
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val separator = detectSeparator(lines.take(5))
        return lines.map { line -> split(line, separator).map { Cell.Text(it) } }
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    private fun detectSeparator(lines: List<String>): Char {
        val semi = lines.sumOf { l -> l.count { it == ';' } }
        val comma = lines.sumOf { l -> l.count { it == ',' } }
        val tab = lines.sumOf { l -> l.count { it == '\t' } }
        return when {
            tab > semi && tab > comma -> '\t'
            semi >= comma && semi > 0 -> ';'
            else -> ','
        }
    }

    internal fun split(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == sep && !quoted -> {
                    out += sb.toString().trim()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }
}
