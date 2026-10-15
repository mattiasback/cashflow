package app.cashflow.feature.import.data.xls

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A cell value as stored in the file. Formatting (dates, decimals) is left to the profiles. */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Number(val value: Double) : Cell
}

/** The first worksheet of a workbook as a sparse grid: rows[row][col]. */
data class Sheet(val name: String, val rows: List<List<Cell?>>)

/**
 * A small in-house BIFF8 reader. It understands only what bank exports use:
 * BOF/EOF, BOUNDSHEET, SST (+ CONTINUE, where a string can switch between
 * compressed and UTF-16 mid-way), LABELSST, LABEL, RSTRING, NUMBER, RK, MULRK and
 * FORMULA results with their STRING record.
 */
object Biff8Workbook {

    fun readFirstSheet(fileBytes: ByteArray): Sheet {
        val stream = CompoundFile(fileBytes).let { it.stream("Workbook") ?: it.stream("Book") }
            ?: throw XlsFormatException("No Workbook stream")
        val records = splitRecords(stream)
        val globalsEnd = records.indexOfFirst { it.type == EOF }
        if (records.firstOrNull()?.type != BOF || globalsEnd < 0) throw XlsFormatException("Bad workbook globals")

        val sst = readSst(records.subList(0, globalsEnd))
        val sheetName = records.subList(0, globalsEnd).firstOrNull { it.type == BOUNDSHEET }?.let(::boundSheetName) ?: ""

        // The first worksheet substream starts at the BOF after the globals' EOF.
        var i = globalsEnd + 1
        while (i < records.size && records[i].type != BOF) i++
        if (i >= records.size) return Sheet(sheetName, emptyList())

        val cells = HashMap<Int, HashMap<Int, Cell>>()
        fun put(row: Int, col: Int, cell: Cell) {
            cells.getOrPut(row) { HashMap() }[col] = cell
        }

        var pendingFormula: Pair<Int, Int>? = null
        i++
        while (i < records.size && records[i].type != EOF) {
            val r = records[i]
            val d = r.data
            val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
            when (r.type) {
                LABELSST -> {
                    val idx = b.getInt(6)
                    sst.getOrNull(idx)?.let { put(u16(b, 0), u16(b, 2), Cell.Text(it)) }
                }
                LABEL, RSTRING -> put(u16(b, 0), u16(b, 2), Cell.Text(readLabel(d, 6)))
                NUMBER -> put(u16(b, 0), u16(b, 2), Cell.Number(b.getDouble(6)))
                RK -> put(u16(b, 0), u16(b, 2), Cell.Number(decodeRk(b.getInt(6))))
                MULRK -> {
                    val row = u16(b, 0)
                    val first = u16(b, 2)
                    val count = (d.size - 6) / 6
                    for (k in 0 until count) {
                        put(row, first + k, Cell.Number(decodeRk(b.getInt(4 + k * 6 + 2))))
                    }
                }
                FORMULA -> {
                    val row = u16(b, 0)
                    val col = u16(b, 2)
                    val isSpecial = (d[12].toInt() and 0xFF) == 0xFF && (d[13].toInt() and 0xFF) == 0xFF
                    if (!isSpecial) put(row, col, Cell.Number(b.getDouble(6)))
                    else if (d[6].toInt() == 0) pendingFormula = row to col
                }
                STRING -> {
                    pendingFormula?.let { (row, col) -> put(row, col, Cell.Text(readUnicodeString(d, 0, withLength16 = true))) }
                    pendingFormula = null
                }
            }
            i++
        }

        val maxRow = cells.keys.maxOrNull() ?: -1
        val rows = (0..maxRow).map { row ->
            val line = cells[row] ?: return@map emptyList()
            val maxCol = line.keys.max()
            (0..maxCol).map { line[it] }
        }
        return Sheet(sheetName, rows)
    }

    private class Record(val type: Int, val data: ByteArray)

    private fun splitRecords(stream: ByteArray): List<Record> {
        val b = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
        val out = ArrayList<Record>()
        var pos = 0
        while (pos + 4 <= stream.size) {
            val type = u16(b, pos)
            val len = u16(b, pos + 2)
            if (pos + 4 + len > stream.size) break
            out += Record(type, stream.copyOfRange(pos + 4, pos + 4 + len))
            pos += 4 + len
            if (type == 0 && len == 0) break
        }
        return out
    }

    private fun boundSheetName(r: Record): String {
        val len = r.data[6].toInt() and 0xFF
        val flags = r.data[7].toInt() and 0xFF
        return if (flags and 1 == 1) String(r.data, 8, len * 2, Charsets.UTF_16LE)
        else String(r.data, 8, len, Charsets.ISO_8859_1)
    }

    /** SST strings may be split over CONTINUE records; each continuation restarts with an option byte. */
    private fun readSst(globals: List<Record>): List<String> {
        val start = globals.indexOfFirst { it.type == SST }
        if (start < 0) return emptyList()
        val segments = ArrayList<ByteArray>()
        segments += globals[start].data
        var j = start + 1
        while (j < globals.size && globals[j].type == CONTINUE) segments += globals[j++].data

        val reader = SegmentReader(segments)
        reader.skip(4) // total count
        val unique = reader.int()
        val out = ArrayList<String>(unique.coerceAtMost(100_000))
        repeat(unique) {
            if (reader.atEnd()) return out
            val cch = reader.u16()
            val flags = reader.u8()
            val rich = flags and 0x08 != 0
            val ext = flags and 0x04 != 0
            val runs = if (rich) reader.u16() else 0
            val extSize = if (ext) reader.int() else 0
            out += reader.chars(cch, wide = flags and 0x01 != 0)
            reader.skip(runs * 4 + extSize)
        }
        return out
    }

    private class SegmentReader(private val segments: List<ByteArray>) {
        private var seg = 0
        private var pos = 0

        fun atEnd() = seg >= segments.size || (seg == segments.size - 1 && pos >= segments[seg].size)

        private fun ensure() {
            while (seg < segments.size && pos >= segments[seg].size) {
                seg++
                pos = 0
            }
            if (seg >= segments.size) throw XlsFormatException("SST truncated")
        }

        fun u8(): Int {
            ensure()
            return segments[seg][pos++].toInt() and 0xFF
        }

        fun u16(): Int = u8() or (u8() shl 8)
        fun int(): Int = u16() or (u16() shl 16)
        fun skip(n: Int) = repeat(n) { u8() }

        fun chars(count: Int, wide: Boolean): String {
            val sb = StringBuilder(count)
            var isWide = wide
            var remaining = count
            while (remaining > 0) {
                if (pos >= segments[seg].size) {
                    // A string continued in the next CONTINUE record starts with a fresh option byte.
                    seg++
                    pos = 0
                    if (seg >= segments.size) throw XlsFormatException("SST string truncated")
                    isWide = (segments[seg][pos++].toInt() and 0x01) != 0
                }
                val cur = segments[seg]
                if (isWide) {
                    val lo = cur[pos].toInt() and 0xFF
                    val hi = cur[pos + 1].toInt() and 0xFF
                    sb.append(((hi shl 8) or lo).toChar())
                    pos += 2
                } else {
                    sb.append((cur[pos].toInt() and 0xFF).toChar())
                    pos += 1
                }
                remaining--
            }
            return sb.toString()
        }
    }

    private fun readLabel(d: ByteArray, offset: Int): String = readUnicodeString(d, offset, withLength16 = true)

    private fun readUnicodeString(d: ByteArray, offset: Int, withLength16: Boolean): String {
        val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        val cch = if (withLength16) u16(b, offset) else d[offset].toInt() and 0xFF
        val flagsAt = offset + if (withLength16) 2 else 1
        if (flagsAt >= d.size) return ""
        val flags = d[flagsAt].toInt() and 0xFF
        var p = flagsAt + 1
        if (flags and 0x08 != 0) p += 2
        if (flags and 0x04 != 0) p += 4
        return if (flags and 0x01 != 0) {
            String(d, p, minOf(cch * 2, d.size - p), Charsets.UTF_16LE)
        } else {
            String(d, p, minOf(cch, d.size - p), Charsets.ISO_8859_1)
        }
    }

    private fun decodeRk(rk: Int): Double {
        val value = if (rk and 0x02 != 0) {
            (rk shr 2).toDouble()
        } else {
            java.lang.Double.longBitsToDouble((rk.toLong() and 0xFFFFFFFCL) shl 32)
        }
        return if (rk and 0x01 != 0) value / 100.0 else value
    }

    private fun u16(b: ByteBuffer, offset: Int) = b.getShort(offset).toInt() and 0xFFFF

    private const val BOF = 0x0809
    private const val EOF = 0x000A
    private const val BOUNDSHEET = 0x0085
    private const val SST = 0x00FC
    private const val CONTINUE = 0x003C
    private const val LABELSST = 0x00FD
    private const val LABEL = 0x0204
    private const val RSTRING = 0x00D6
    private const val NUMBER = 0x0203
    private const val RK = 0x027E
    private const val MULRK = 0x00BD
    private const val FORMULA = 0x0006
    private const val STRING = 0x0207
}
