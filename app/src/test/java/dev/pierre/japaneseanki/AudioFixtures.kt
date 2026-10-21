package dev.pierre.japaneseanki

import java.io.File
import java.io.FileOutputStream

/**
 * Structurally valid audio fixtures written with plain bytes: no network, no Android, no
 * third-party encoder. The MP3 fixture is one real MPEG-1 Layer III frame header plus its
 * declared payload; the Ogg fixture is one well-formed page. Playback fixtures for the
 * instrumentation suite are separate (they must actually decode on device).
 */
object AudioFixtures {
    fun wav(file: File, sampleFrames: Int = 480): File {
        val data = ByteArray(sampleFrames * 2)
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray())
            .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(16_000).putInt(32_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(data.size)
        file.writeBytes(header.array() + data)
        return file
    }

    fun wavBytes(sampleFrames: Int = 480): ByteArray {
        val temp = File.createTempFile("fixture", ".wav")
        try {
            wav(temp, sampleFrames)
            return temp.readBytes()
        } finally {
            temp.delete()
        }
    }

    /** One Ogg page: capture pattern, BOS header, one segment of [payloadBytes] zeros. */
    fun ogg(file: File, payloadBytes: Int = 32): File {
        require(payloadBytes in 1..255)
        val page = java.nio.ByteBuffer.allocate(28 + payloadBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        page.put("OggS".toByteArray()).put(0).put(2)
            .putLong(0L)
            .putInt(1).putInt(0).putInt(0)
            .put(1).put(payloadBytes.toByte())
            .put(ByteArray(payloadBytes))
        file.writeBytes(page.array())
        return file
    }

    /**
     * MPEG-1 Layer III, 128 kbps, 44.1 kHz, no CRC, no padding: a 417-byte frame. Only the
     * container structure is produced; a real decoder is not part of JVM tests.
     */
    fun mp3(file: File, padding: Boolean = false): File {
        val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), (0x90 or if (padding) 0x02 else 0x00).toByte(), 0x00)
        val frameLength = 417 + if (padding) 1 else 0
        val frame = ByteArray(frameLength)
        header.copyInto(frame)
        file.writeBytes(frame)
        return file
    }

    fun copy(source: File, destination: File): File {
        FileOutputStream(destination).use { output -> source.inputStream().use { it.copyTo(output) } }
        return destination
    }
}
