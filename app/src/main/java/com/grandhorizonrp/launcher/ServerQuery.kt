package com.grandhorizonrp.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal SA-MP master query ('i' opcode) client used for the live server
 * status card.
 */
object ServerQuery {

    data class Info(
        val players: Int,
        val maxPlayers: Int,
        val hostname: String,
        val gamemode: String
    )

    suspend fun query(ip: String, port: Int, timeoutMs: Int = 5000): Info? =
        withContext(Dispatchers.IO) {
            runCatching {
                val addr = InetAddress.getByName(ip)
                val out = ByteArrayOutputStream()
                out.write("SAMP".toByteArray(Charsets.US_ASCII))
                out.write(addr.address)
                out.write(byteArrayOf((port shr 8).toByte(), (port and 0xFF).toByte()))
                out.write('i'.code)
                val payload = out.toByteArray()

                DatagramSocket().use { sock ->
                    sock.soTimeout = timeoutMs
                    sock.send(DatagramPacket(payload, payload.size, addr, port))
                    val buf = ByteArray(2048)
                    val resp = DatagramPacket(buf, buf.size)
                    sock.receive(resp)
                    parse(resp.data, resp.length)
                }
            }.getOrNull()
        }

    private fun parse(data: ByteArray, length: Int): Info? {
        if (length < 15) return null
        val bb = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        bb.get(magic)
        if (String(magic, Charsets.US_ASCII) != "SAMP") return null
        bb.position(11) // skip SAMP + ip(4) + port(2) + opcode(1)
        bb.get() // password flag
        val players = bb.short.toInt() and 0xFFFF
        val maxPlayers = bb.short.toInt() and 0xFFFF

        fun readStr(): String {
            val len = bb.short.toInt() and 0xFFFF
            if (len <= 0 || bb.remaining() < len) return ""
            val b = ByteArray(len)
            bb.get(b)
            return String(b, Charsets.UTF_8)
        }

        val hostname = readStr()
        val gamemode = readStr()
        return Info(players, maxPlayers, hostname, gamemode)
    }
}
