package app.cashflow.feature.importer.data

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Test-only writer for small but structurally real BIFF8 `.xls` files (OLE2 container,
 * Workbook stream, SST with CONTINUE, LABELSST/NUMBER/RK/MULRK cells). Fixtures use fake
 * names and amounts; real bank exports never go into the repo.
 */
object SyntheticXls {

    sealed interface Value {
        data class Text(val s: String) : Value
        data class Num(val d: Double) : Value

        /** Stored as an RK record (integer or ×100 encodings). */
        data class Rk(val d: Double) : Value
    }

    /**
     * @param splitSstAt when set, the SST record is cut after this many bytes so a string
     *   continues in a CONTINUE record (and may switch between compressed and UTF-16 there).
     * @param mulRkRow a row index whose numeric cells are written as one MULRK record.
     */
    fun workbook(
        rows: List<List<Value?>>,
        sheetName: String = "Kontoutdrag",
        splitSstAt: Int? = null,
        mulRkRow: Int? = null,
    ): ByteArray {
        val strings = rows.flatten().filterIsInstance<Value.Text>().map { it.s }.distinct()
        val globals = ByteArrayOutputStream()
        globals.record(0x0809, bof(0x0005))
        val boundSheetAt = globals.size()
        globals.record(0x0085, ByteArray(6) + byteArrayOf(sheetName.length.toByte(), 0) + sheetName.toByteArray(Charsets.ISO_8859_1))
        writeSst(globals, strings, splitSstAt)
        globals.record(0x000A, ByteArray(0))

        val sheet = ByteArrayOutputStream()
        sheet.record(0x0809, bof(0x0010))
        rows.forEachIndexed { r, row ->
            if (r == mulRkRow && row.all { it is Value.Rk }) {
                val b = le(4 + row.size * 6 + 2)
                b.putShort(r.toShort()).putShort(0)
                row.forEach { b.putShort(15).putInt(encodeRk((it as Value.Rk).d)) }
                b.putShort((row.size - 1).toShort())
                sheet.record(0x00BD, b.array())
                return@forEachIndexed
            }
            row.forEachIndexed { c, v ->
                when (v) {
                    is Value.Text -> sheet.record(0x00FD, le(10).putShort(r.toShort()).putShort(c.toShort()).putShort(15).putInt(strings.indexOf(v.s)).array())
                    is Value.Num -> sheet.record(0x0203, le(14).putShort(r.toShort()).putShort(c.toShort()).putShort(15).putDouble(v.d).array())
                    is Value.Rk -> sheet.record(0x027E, le(10).putShort(r.toShort()).putShort(c.toShort()).putShort(15).putInt(encodeRk(v.d)).array())
                    null -> Unit
                }
            }
        }
        sheet.record(0x000A, ByteArray(0))

        val g = globals.toByteArray()
        // BOUNDSHEET points at the sheet's BOF: the offset right after the globals.
        ByteBuffer.wrap(g).order(ByteOrder.LITTLE_ENDIAN).putInt(boundSheetAt + 4, g.size)
        var stream = g + sheet.toByteArray()
        // Keep the stream above the 4 096-byte mini-stream cutoff so it lives in normal sectors.
        if (stream.size < 4096) stream += ByteArray(4096 - stream.size)
        return compoundFile(stream)
    }

    private fun bof(type: Int) = le(16).putShort(0x0600).putShort(type.toShort()).putShort(0x0DBB).putShort(0x07CC).putInt(0).putInt(0x06).array()

    private fun writeSst(out: ByteArrayOutputStream, strings: List<String>, splitAt: Int?) {
        val body = ByteArrayOutputStream()
        body.write(le(8).putInt(strings.size).putInt(strings.size).array())
        // Remember where each string's characters start so a split can insert an option byte.
        val charStarts = ArrayList<Triple<Int, Int, Boolean>>() // offset, length in bytes, wide
        strings.forEach { s ->
            val wide = s.any { it.code > 0xFF }
            body.write(le(2).putShort(s.length.toShort()).array())
            body.write(if (wide) 1 else 0)
            val chars = if (wide) s.toByteArray(Charsets.UTF_16LE) else s.toByteArray(Charsets.ISO_8859_1)
            charStarts += Triple(body.size(), chars.size, wide)
            body.write(chars)
        }
        val bytes = body.toByteArray()
        if (splitAt == null || splitAt >= bytes.size) {
            out.record(0x00FC, bytes)
            return
        }
        val inString = charStarts.firstOrNull { (start, len, _) -> splitAt in start until start + len }
        // Excel never splits a UTF-16 character in half.
        require(inString == null || !inString.third || (splitAt - inString.first) % 2 == 0) { "split inside a character" }
        out.record(0x00FC, bytes.copyOfRange(0, splitAt))
        val rest = bytes.copyOfRange(splitAt, bytes.size)
        if (inString != null) {
            // A continued string restarts with its option byte (0 = compressed, 1 = UTF-16).
            out.record(0x003C, byteArrayOf(if (inString.third) 1 else 0) + rest)
        } else {
            out.record(0x003C, rest)
        }
    }

    private fun encodeRk(d: Double): Int {
        val asInt = d.toLong()
        if (asInt.toDouble() == d && asInt in -(1L shl 29) until (1L shl 29)) return (asInt.toInt() shl 2) or 0x02
        val times100 = Math.round(d * 100)
        if (times100 / 100.0 == d && times100 in -(1L shl 29) until (1L shl 29)) return (times100.toInt() shl 2) or 0x03
        error("value $d is not RK-encodable in this test writer")
    }

    private fun compoundFile(stream: ByteArray): ByteArray {
        val sectorSize = 512
        val streamSectors = (stream.size + sectorSize - 1) / sectorSize
        val fatSector = 0
        val dirSector = 1
        val firstStreamSector = 2
        val fat = IntArray(sectorSize / 4) { -1 } // FREESECT
        fat[fatSector] = -3 // FATSECT
        fat[dirSector] = -2 // ENDOFCHAIN
        for (i in 0 until streamSectors) fat[firstStreamSector + i] = if (i == streamSectors - 1) -2 else firstStreamSector + i + 1

        val header = le(sectorSize)
        header.putLong(0, -0x1ee54e5e1fee3030L)
        header.putShort(0x18, 0x003E).putShort(0x1A, 0x0003).putShort(0x1C, 0xFFFE.toShort())
        header.putShort(0x1E, 9).putShort(0x20, 6)
        header.putInt(0x2C, 1).putInt(0x30, dirSector).putInt(0x38, 4096)
        header.putInt(0x3C, -2).putInt(0x40, 0).putInt(0x44, -2).putInt(0x48, 0)
        for (i in 0 until 109) header.putInt(0x4C + i * 4, if (i == 0) fatSector else -1)

        val fatBytes = le(sectorSize).also { b -> fat.forEach { b.putInt(it) } }.array()
        val dir = le(sectorSize)
        dirEntry(dir, 0, "Root Entry", type = 5, child = 1, start = -2, size = 0)
        dirEntry(dir, 128, "Workbook", type = 2, child = -1, start = firstStreamSector, size = stream.size)
        for (i in 2 until 4) dirEntry(dir, i * 128, "", type = 0, child = -1, start = 0, size = 0)

        val padded = stream.copyOf(streamSectors * sectorSize)
        return header.array() + fatBytes + dir.array() + padded
    }

    private fun dirEntry(b: ByteBuffer, at: Int, name: String, type: Int, child: Int, start: Int, size: Int) {
        val n = name.toByteArray(Charsets.UTF_16LE)
        for (i in n.indices) b.put(at + i, n[i])
        b.putShort(at + 0x40, if (name.isEmpty()) 0 else (n.size + 2).toShort())
        b.put(at + 0x42, type.toByte())
        b.put(at + 0x43, 1)
        b.putInt(at + 0x44, -1).putInt(at + 0x48, -1).putInt(at + 0x4C, child)
        b.putInt(at + 0x74, start).putInt(at + 0x78, size)
    }

    private fun le(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun ByteArrayOutputStream.record(type: Int, data: ByteArray) {
        write(le(4).putShort(type.toShort()).putShort(data.size.toShort()).array())
        write(data)
    }
}
