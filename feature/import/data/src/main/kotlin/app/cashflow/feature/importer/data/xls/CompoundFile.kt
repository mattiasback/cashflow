package app.cashflow.feature.importer.data.xls

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal reader for OLE2 compound files (the container of Excel 97–2003 `.xls`).
 * It follows the FAT, mini-FAT and DIFAT chains and returns a named stream.
 */
internal class CompoundFile(private val bytes: ByteArray) {

    private val buf: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniStreamCutoff: Int
    private val fat: IntArray
    private val miniFat: IntArray
    private val entries: List<DirEntry>
    private val miniStream: ByteArray

    init {
        if (bytes.size < HEADER_SIZE || buf.getLong(0) != SIGNATURE) throw XlsFormatException("Not an OLE2 file")
        sectorSize = 1 shl u16(0x1E)
        miniSectorSize = 1 shl u16(0x20)
        miniStreamCutoff = buf.getInt(0x38)
        fat = readFat()
        entries = readDirectory(buf.getInt(0x30))
        val root = entries.firstOrNull { it.type == TYPE_ROOT } ?: throw XlsFormatException("No root entry")
        miniStream = readChain(root.startSector, root.size, fat, sectorSize, ::sectorBytes)
        miniFat = readMiniFat(buf.getInt(0x3C), buf.getInt(0x40))
    }

    fun stream(name: String): ByteArray? {
        val entry = entries.firstOrNull { it.type == TYPE_STREAM && it.name.equals(name, ignoreCase = true) } ?: return null
        return if (entry.size < miniStreamCutoff) {
            readChain(entry.startSector, entry.size, miniFat, miniSectorSize) { s ->
                val off = s * miniSectorSize
                if (off + miniSectorSize > miniStream.size) throw XlsFormatException("Mini sector out of range")
                miniStream.copyOfRange(off, off + miniSectorSize)
            }
        } else {
            readChain(entry.startSector, entry.size, fat, sectorSize, ::sectorBytes)
        }
    }

    private fun u16(offset: Int) = buf.getShort(offset).toInt() and 0xFFFF

    private fun sectorOffset(sector: Int): Int = (sector + 1) * sectorSize

    private fun sectorBytes(sector: Int): ByteArray {
        val off = sectorOffset(sector)
        if (sector < 0 || off + sectorSize > bytes.size) throw XlsFormatException("Sector $sector out of range")
        return bytes.copyOfRange(off, off + sectorSize)
    }

    private fun readFat(): IntArray {
        val fatSectors = ArrayList<Int>()
        for (i in 0 until 109) {
            val s = buf.getInt(0x4C + i * 4)
            if (s >= 0) fatSectors += s
        }
        var difat = buf.getInt(0x44)
        var guard = 0
        val perSector = sectorSize / 4
        while (difat >= 0 && guard++ < 10_000) {
            val sb = ByteBuffer.wrap(sectorBytes(difat)).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until perSector - 1) {
                val s = sb.getInt(i * 4)
                if (s >= 0) fatSectors += s
            }
            difat = sb.getInt((perSector - 1) * 4)
        }
        val out = IntArray(fatSectors.size * perSector)
        fatSectors.forEachIndexed { idx, s ->
            val sb = ByteBuffer.wrap(sectorBytes(s)).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until perSector) out[idx * perSector + i] = sb.getInt(i * 4)
        }
        return out
    }

    private fun readMiniFat(start: Int, count: Int): IntArray {
        if (count <= 0 || start < 0) return IntArray(0)
        val raw = readChain(start, count.toLong() * sectorSize, fat, sectorSize, ::sectorBytes)
        val b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(raw.size / 4) { b.getInt(it * 4) }
    }

    private fun readDirectory(start: Int): List<DirEntry> {
        val raw = readChain(start, -1, fat, sectorSize, ::sectorBytes)
        val b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return (0 until raw.size / DIR_ENTRY_SIZE).mapNotNull { i ->
            val base = i * DIR_ENTRY_SIZE
            val nameLen = (b.getShort(base + 0x40).toInt() and 0xFFFF).coerceIn(0, 64)
            val type = b.get(base + 0x42).toInt()
            if (type == 0) return@mapNotNull null
            val name = String(raw, base, (nameLen - 2).coerceAtLeast(0), Charsets.UTF_16LE)
            DirEntry(
                name = name,
                type = type,
                startSector = b.getInt(base + 0x74),
                size = b.getInt(base + 0x78).toLong() and 0xFFFFFFFFL,
            )
        }
    }

    /** Follows a sector chain. A negative [size] reads until the end of the chain. */
    private inline fun readChain(
        start: Int,
        size: Long,
        table: IntArray,
        unit: Int,
        read: (Int) -> ByteArray,
    ): ByteArray {
        if (size == 0L) return ByteArray(0)
        val out = java.io.ByteArrayOutputStream()
        var s = start
        var guard = 0
        while (s >= 0 && guard++ < table.size + 1) {
            out.write(read(s))
            if (size >= 0 && out.size() >= size) break
            s = if (s < table.size) table[s] else END_OF_CHAIN
        }
        val all = out.toByteArray()
        return if (size in 0..all.size.toLong()) all.copyOf(size.toInt()) else all
    }

    private data class DirEntry(val name: String, val type: Int, val startSector: Int, val size: Long)

    private companion object {
        const val SIGNATURE = -0x1ee54e5e1fee3030L // D0 CF 11 E0 A1 B1 1A E1, little-endian
        const val HEADER_SIZE = 512
        const val DIR_ENTRY_SIZE = 128
        const val TYPE_STREAM = 2
        const val TYPE_ROOT = 5
        const val END_OF_CHAIN = -2
    }
}

class XlsFormatException(message: String) : Exception(message)
