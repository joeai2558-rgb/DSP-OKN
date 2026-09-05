package com.okn.dsp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PacketLog {
    private val lines = mutableListOf<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    fun add(direction: String, uuid: String, bytes: ByteArray) {
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        lines += "${fmt.format(Date())} $direction $uuid | $hex"
    }
    fun text(): String = lines.joinToString("\n")
    fun clear() { lines.clear() }
}
