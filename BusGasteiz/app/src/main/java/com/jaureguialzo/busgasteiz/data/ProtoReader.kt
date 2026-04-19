package com.jaureguialzo.busgasteiz.data

// MARK: - Lector de Protocol Buffers (wire format manual)
// Puerto Kotlin de ProtoReader.swift — misma lógica, sin dependencias externas

class ProtoReader(private val data: ByteArray) {
    var position: Int = 0

    val hasMore: Boolean get() = position < data.size

    fun readVarint(): ULong? {
        var result: ULong = 0u
        var shift = 0
        while (position < data.size) {
            val byte = data[position].toInt() and 0xFF
            position++
            result = result or ((byte and 0x7F).toULong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            if (shift >= 64) return null
        }
        return null
    }

    fun readLengthDelimited(): ByteArray? {
        val length = readVarint() ?: return null
        if (length > Int.MAX_VALUE.toULong()) return null
        val len = length.toInt()
        if (position + len > data.size) return null
        val result = data.copyOfRange(position, position + len)
        position += len
        return result
    }

    fun readTag(): Pair<Int, Int>? {
        val tag = readVarint() ?: return null
        val field = (tag shr 3).toInt()
        val wire = (tag and 0x7u).toInt()
        return Pair(field, wire)
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> position = minOf(position + 8, data.size)
            2 -> readLengthDelimited()
            5 -> position = minOf(position + 4, data.size)
            else -> position = data.size
        }
    }
}
